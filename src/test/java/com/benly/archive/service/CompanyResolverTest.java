package com.benly.archive.service;

import com.benly.archive.entity.Company;
import com.benly.archive.entity.CompanyAlias;
import com.benly.archive.entity.CompanySource;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.CompanyAliasRepository;
import com.benly.archive.repository.CompanyRepository;
import com.benly.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompanyResolverTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CompanyAliasRepository companyAliasRepository;

    @InjectMocks
    private CompanyResolver companyResolver;

    @Test
    @DisplayName("같은 이름의 회사가 있으면 그 회사를 쓰고 새로 만들지 않는다")
    void foundByName() {
        Company kakao = Company.ofDart(null, "카카오", "카카오");
        given(companyRepository.findByNormalizedName("카카오")).willReturn(Optional.of(kakao));

        ResolvedCompany result = companyResolver.findOrCreate("(주)카카오");

        assertThat(result.company()).isSameAs(kakao);
        assertThat(result.created()).isFalse();
        verify(companyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이름은 없고 별칭이 있으면 별칭의 회사를 쓴다")
    void foundByAlias() {
        Company toss = Company.ofDart(null, "비바리퍼블리카", "비바리퍼블리카");
        given(companyRepository.findByNormalizedName("토스")).willReturn(Optional.empty());
        given(companyAliasRepository.findByNormalizedAlias("토스"))
                .willReturn(Optional.of(CompanyAlias.of(toss, "토스", "토스")));

        ResolvedCompany result = companyResolver.findOrCreate("토스");

        assertThat(result.company().getName()).isEqualTo("비바리퍼블리카");
        assertThat(result.created()).isFalse();
    }

    @Test
    @DisplayName("이름도 별칭도 없으면 직접 입력 회사로 새로 만든다")
    void createUserCompany() {
        given(companyRepository.findByNormalizedName("로켓펀치")).willReturn(Optional.empty());
        given(companyAliasRepository.findByNormalizedAlias("로켓펀치")).willReturn(Optional.empty());
        given(companyRepository.saveAndFlush(any(Company.class))).willAnswer(inv -> inv.getArgument(0));

        ResolvedCompany result = companyResolver.findOrCreate("  로켓펀치 ");

        assertThat(result.created()).isTrue();
        assertThat(result.company().getName()).isEqualTo("로켓펀치");
        assertThat(result.company().getNormalizedName()).isEqualTo("로켓펀치");
        assertThat(result.company().getSource()).isEqualTo(CompanySource.USER);
    }

    @Test
    @DisplayName("동시에 같은 이름이 먼저 저장되면 그 회사를 다시 찾아 쓴다")
    void concurrentCreate() {
        Company winner = Company.ofUser("로켓펀치", "로켓펀치");
        given(companyRepository.findByNormalizedName("로켓펀치"))
                .willReturn(Optional.empty(), Optional.of(winner));
        given(companyAliasRepository.findByNormalizedAlias("로켓펀치")).willReturn(Optional.empty());
        given(companyRepository.saveAndFlush(any(Company.class)))
                .willThrow(new DataIntegrityViolationException("uk_company_normalized_name"));

        ResolvedCompany result = companyResolver.findOrCreate("로켓펀치");

        assertThat(result.company()).isSameAs(winner);
        assertThat(result.created()).isFalse();
    }

    @Test
    @DisplayName("정규화 후 2자 미만이면 400")
    void tooShortName() {
        assertThatThrownBy(() -> companyResolver.findOrCreate("(주)A"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ArchiveErrorCode.COMPANY_NAME_TOO_SHORT);
    }
}
