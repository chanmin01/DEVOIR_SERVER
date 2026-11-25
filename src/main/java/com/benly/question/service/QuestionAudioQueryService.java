package com.benly.question.service;

import com.benly.global.exception.BusinessException;
import com.benly.question.entity.Question;
import com.benly.question.exception.AnswerErrorCode;
import com.benly.question.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionAudioQueryService {

    private final QuestionRepository questionRepository;

    /**
     * 본인 세션의 질문이면 문장을 돌려준다.
     * 답변 제출과 달리 진행 상태·순서는 확인하지 않는다. 듣기만 하는 요청이라 데이터를 바꾸지 않는다.
     */
    public String findOwnedQuestionContent(Long questionId, Long userId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(AnswerErrorCode.QUESTION_NOT_FOUND));

        if (!question.getSession().getUser().getId().equals(userId)) {
            throw new BusinessException(AnswerErrorCode.ANSWER_FORBIDDEN);
        }

        return question.getContent();
    }
}
