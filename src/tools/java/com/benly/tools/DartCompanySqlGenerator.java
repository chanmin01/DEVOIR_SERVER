package com.benly.tools;

import com.benly.archive.support.CompanyNameNormalizer;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * OpenDART 고유번호 목록에서 현재 상장사를 추려 회사 적재 마이그레이션 SQL을 만드는 일회성 도구.
 * 고유번호 목록의 종목코드는 상장폐지나 합병 후에도 남아 있어서, 기업개황 API의 법인구분으로 현재 상장 여부를 다시 확인한다.
 * tools 소스셋에 있어서 운영 jar와 테스트에 포함되지 않는다.
 * 실행: DART_API_KEY=발급키 ./gradlew generateDartCompanySql
 * 이미 적용된 마이그레이션은 덮어쓰지 않는다. 다시 만들 때는 새 버전 파일 경로를 넘긴다.
 * 예: ./gradlew generateDartCompanySql --args="src/main/resources/db/migration/V20__refresh_dart_listed_companies.sql"
 */
public final class DartCompanySqlGenerator {

    private static final String API_BASE = "https://opendart.fss.or.kr/api/";
    private static final Path DEFAULT_OUTPUT =
            Path.of("src/main/resources/db/migration/V13__seed_dart_listed_companies.sql");
    private static final Path CLASS_CACHE = Path.of("build/dart/corp_cls.tsv");

    /** 기업개황 법인구분: Y 유가증권, K 코스닥, N 코넥스, E 기타 */
    private static final Set<String> LISTED_CLASSES = Set.of("Y", "K", "N");
    private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"(\\d+)\"");
    private static final Pattern CORP_CLS = Pattern.compile("\"corp_cls\"\\s*:\\s*\"([A-Z]*)\"");
    private static final String STATUS_OK = "000";
    private static final String STATUS_NO_DATA = "013";
    private static final long REQUEST_INTERVAL_MILLIS = 100;
    private static final int PROGRESS_STEP = 200;

    private static final int CORP_CODE_LENGTH = 8;
    private static final int MIN_KEY_LENGTH = 2;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int BATCH_SIZE = 500;

    /**
     * V2에서 넣은 시드 회사 중 현재 상장사인 회사와 DART 공식 회사명의 짝.
     * 시드 이름과 DART 이름이 다른 회사가 있어서 직접 지정하고, 시드에 고유번호를 먼저 채워 같은 회사가 두 번 들어가지 않게 한다.
     * 비상장 시드는 상장사 목록과 겹칠 일이 없어서 넣지 않는다.
     */
    private static final List<SeedMapping> SEEDS = List.of(
            new SeedMapping("삼성전자", "삼성전자"),
            new SeedMapping("sk하이닉스", "SK하이닉스"),
            new SeedMapping("lgcns", "LG씨엔에스"),
            new SeedMapping("네이버", "NAVER"),
            new SeedMapping("카카오", "카카오"),
            new SeedMapping("카카오뱅크", "카카오뱅크"),
            new SeedMapping("카카오페이", "카카오페이")
    );

    /** 기업인수목적회사는 실제 채용 회사가 아니라서 자동완성에서 뺀다. */
    private static final Pattern SPAC_NAME = Pattern.compile("스팩|기업인수목적");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String apiKey;
    private final List<String> warnings = new ArrayList<>();

    private DartCompanySqlGenerator(String apiKey) {
        this.apiKey = apiKey;
    }

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("DART_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("환경변수 DART_API_KEY가 없습니다.");
        }
        Path output = args.length > 0 ? Path.of(args[0]) : DEFAULT_OUTPUT;
        if (Files.exists(output)) {
            throw new IllegalStateException(output + " 파일이 이미 있습니다. 적용된 마이그레이션을 덮어쓰면 Flyway 검증이 실패하니 새 버전 파일 경로를 인자로 넘겨주세요.");
        }

        DartCompanySqlGenerator generator = new DartCompanySqlGenerator(apiKey.trim());
        List<Corp> corps = generator.readCorps(generator.downloadCorpCodes());
        Set<String> listedCodes = generator.findCurrentlyListed(corps);
        String sql = generator.buildSql(corps, listedCodes);

        Files.writeString(output, sql, StandardCharsets.UTF_8);
        System.out.println("생성 완료: " + output.toAbsolutePath());
        generator.warnings.forEach(w -> System.err.println("[확인 필요] " + w));
    }

    private byte[] downloadCorpCodes() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + "corpCode.xml?crtfc_key=" + apiKey))
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("DART 응답 코드: " + response.statusCode());
        }

        byte[] body = response.body();
        boolean isZip = body.length > 1 && body[0] == 'P' && body[1] == 'K';
        if (!isZip) {
            String preview = new String(body, 0, Math.min(body.length, 300), StandardCharsets.UTF_8);
            throw new IllegalStateException("DART 응답이 zip이 아닙니다: " + preview);
        }
        return body;
    }

    private List<Corp> readCorps(byte[] zip) throws IOException, XMLStreamException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                    return parse(zis);
                }
            }
        }
        throw new IllegalStateException("zip 안에 XML 파일이 없습니다.");
    }

    private List<Corp> parse(InputStream in) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader reader = factory.createXMLStreamReader(in, "UTF-8");

        List<Corp> corps = new ArrayList<>();
        Map<String, String> fields = new HashMap<>();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tag = reader.getLocalName();
                switch (tag) {
                    case "list" -> fields.clear();
                    case "corp_code", "corp_name", "stock_code" -> fields.put(tag, reader.getElementText().trim());
                    default -> {
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "list".equals(reader.getLocalName())) {
                corps.add(new Corp(
                        fields.getOrDefault("corp_code", ""),
                        fields.getOrDefault("corp_name", ""),
                        fields.getOrDefault("stock_code", "")));
            }
        }
        reader.close();
        return corps;
    }

    private Set<String> findCurrentlyListed(List<Corp> corps) throws IOException, InterruptedException {
        Map<String, String> cache = loadClassCache();
        List<Corp> targets = corps.stream().filter(Corp::hasStockCode).toList();

        Set<String> listed = new HashSet<>();
        int fetched = 0;
        for (int i = 0; i < targets.size(); i++) {
            Corp corp = targets.get(i);
            String corpClass = cache.get(corp.corpCode());
            if (corpClass == null) {
                corpClass = fetchCorpClass(corp.corpCode());
                appendClassCache(corp.corpCode(), corpClass);
                fetched++;
                Thread.sleep(REQUEST_INTERVAL_MILLIS);
            }
            if (LISTED_CLASSES.contains(corpClass)) {
                listed.add(corp.corpCode());
            }
            if ((i + 1) % PROGRESS_STEP == 0) {
                System.out.println("상장 여부 확인 " + (i + 1) + "/" + targets.size());
            }
        }

        System.out.println("종목코드가 있는 회사 " + targets.size() + "개 중 현재 상장사 " + listed.size()
                + "개, 이번에 새로 조회 " + fetched + "건");
        return listed;
    }

    private String fetchCorpClass(String corpCode) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(API_BASE + "company.json?crtfc_key=" + apiKey + "&corp_code=" + corpCode))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("DART 기업개황 응답 코드: " + response.statusCode() + ", corp_code=" + corpCode);
        }

        String body = response.body();
        String status = find(STATUS, body);
        if (STATUS_NO_DATA.equals(status)) {
            return "";
        }
        if (!STATUS_OK.equals(status)) {
            throw new IllegalStateException("DART 기업개황 조회 실패, corp_code=" + corpCode + ", status=" + status
                    + ". 020은 요청 한도 초과라서 다음 날 다시 실행하면 캐시에서 이어서 진행한다.");
        }
        return find(CORP_CLS, body);
    }

    private static String find(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static Map<String, String> loadClassCache() throws IOException {
        Map<String, String> cache = new HashMap<>();
        if (!Files.exists(CLASS_CACHE)) {
            return cache;
        }
        for (String line : Files.readAllLines(CLASS_CACHE, StandardCharsets.UTF_8)) {
            String[] parts = line.split("\t", -1);
            if (parts.length == 2) {
                cache.put(parts[0], parts[1]);
            }
        }
        return cache;
    }

    private static void appendClassCache(String corpCode, String corpClass) throws IOException {
        Files.createDirectories(CLASS_CACHE.getParent());
        Files.writeString(CLASS_CACHE, corpCode + "\t" + corpClass + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private String buildSql(List<Corp> corps, Set<String> listedCodes) {
        Map<String, List<Corp>> allByKey = new HashMap<>();
        for (Corp corp : corps) {
            allByKey.computeIfAbsent(CompanyNameNormalizer.normalize(corp.name()), k -> new ArrayList<>()).add(corp);
        }

        StringBuilder seedSql = new StringBuilder();
        Set<String> seedCorpCodes = new HashSet<>();
        for (SeedMapping seed : SEEDS) {
            Corp matched = resolveSeed(seed, allByKey, listedCodes);
            seedCorpCodes.add(matched.corpCode());
            seedSql.append("UPDATE companies SET corp_code = '").append(matched.corpCode())
                    .append("' WHERE normalized_name = '").append(escape(seed.seedKey()))
                    .append("' AND corp_code IS NULL;\n");
        }

        Map<String, Corp> listedByKey = new LinkedHashMap<>();
        int spacCount = 0;
        for (Corp corp : corps) {
            if (!listedCodes.contains(corp.corpCode()) || seedCorpCodes.contains(corp.corpCode())) {
                continue;
            }
            if (SPAC_NAME.matcher(corp.name()).find()) {
                spacCount++;
                continue;
            }
            if (corp.corpCode().length() != CORP_CODE_LENGTH || corp.name().isBlank()
                    || corp.name().length() > MAX_NAME_LENGTH) {
                warnings.add("형식이 맞지 않아 건너뜀: " + corp);
                continue;
            }
            String key = CompanyNameNormalizer.normalize(corp.name());
            if (key.length() < MIN_KEY_LENGTH) {
                warnings.add("정규화 후 이름이 너무 짧아 건너뜀: " + corp);
                continue;
            }
            Corp previous = listedByKey.putIfAbsent(key, corp);
            if (previous != null) {
                warnings.add("현재 상장사 중 정규화 이름이 같아 뒤의 회사를 건너뜀: " + previous + " / " + corp);
            }
        }

        System.out.println("기업인수목적회사 " + spacCount + "개 제외");

        List<Map.Entry<String, Corp>> rows = listedByKey.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().corpCode()))
                .toList();

        StringBuilder sql = new StringBuilder();
        sql.append("-- DartCompanySqlGenerator로 생성, 기준일 ").append(LocalDate.now())
                .append(", 현재 상장사 ").append(rows.size()).append("개\n\n");

        sql.append("-- 1. 기존 시드 회사에 DART 고유번호 채우기\n");
        sql.append(seedSql).append('\n');

        sql.append("-- 2. 상장사 목록을 임시 테이블에 담기\n");
        sql.append("""
                CREATE TEMPORARY TABLE dart_listed_companies (
                    corp_code       VARCHAR(8)   NOT NULL,
                    name            VARCHAR(100) NOT NULL,
                    normalized_name VARCHAR(100) NOT NULL,
                    PRIMARY KEY (corp_code)
                );

                """);
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            List<Map.Entry<String, Corp>> batch = rows.subList(i, Math.min(i + BATCH_SIZE, rows.size()));
            sql.append("INSERT INTO dart_listed_companies (corp_code, name, normalized_name) VALUES\n");
            for (int j = 0; j < batch.size(); j++) {
                Map.Entry<String, Corp> row = batch.get(j);
                sql.append("('").append(row.getValue().corpCode()).append("', '")
                        .append(escape(row.getValue().name())).append("', '")
                        .append(escape(row.getKey())).append("')")
                        .append(j == batch.size() - 1 ? ";\n\n" : ",\n");
            }
        }

        sql.append("""
                -- 3. 직접 입력으로 생긴 회사 중 정규화 이름이 같은 곳은 DART 회사로 승격
                UPDATE companies c
                    JOIN dart_listed_companies d ON d.normalized_name = c.normalized_name
                SET c.corp_code = d.corp_code,
                    c.source    = 'DART',
                    c.name      = d.name
                WHERE c.source = 'USER';

                -- 4. 나머지 상장사 적재, 이미 있는 이름과 고유번호, 기존 별칭과 겹치는 이름은 건너뜀
                INSERT INTO companies (corp_code, name, normalized_name, source, created_at)
                SELECT d.corp_code, d.name, d.normalized_name, 'DART', NOW(6)
                FROM dart_listed_companies d
                WHERE NOT EXISTS (SELECT 1 FROM companies c
                                  WHERE c.normalized_name = d.normalized_name
                                     OR c.corp_code = d.corp_code)
                  AND NOT EXISTS (SELECT 1 FROM company_aliases a
                                  WHERE a.normalized_alias = d.normalized_name);

                DROP TEMPORARY TABLE dart_listed_companies;
                """);
        return sql.toString();
    }

    private static Corp resolveSeed(SeedMapping seed, Map<String, List<Corp>> allByKey, Set<String> listedCodes) {
        List<Corp> listed = allByKey.getOrDefault(CompanyNameNormalizer.normalize(seed.dartName()), List.of())
                .stream()
                .filter(c -> listedCodes.contains(c.corpCode()))
                .toList();
        if (listed.size() != 1) {
            throw new IllegalStateException("시드 " + seed.seedKey() + "와 맞는 현재 상장사가 " + listed.size()
                    + "개입니다: " + listed);
        }
        return listed.getFirst();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    private record SeedMapping(String seedKey, String dartName) {
    }

    private record Corp(String corpCode, String name, String stockCode) {

        boolean hasStockCode() {
            return !stockCode.isBlank();
        }
    }
}
