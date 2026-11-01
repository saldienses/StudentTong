package com.tihai.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tihai.common.Question;
import com.tihai.constant.QueryResponseConstant;
import com.tihai.constant.QuestionTypeConstant;
import com.tihai.vo.QueryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * @Copyright : DuanInnovator
 * @Description : 查题工具类
 * @Author : DuanInnovator
 * @CreateTime : 2025/4/24
 * @Link : <a href="https://github.com/DuanInnovator/TiHaiWuYou-Admin/tree/mine-admin">...</a>
 **/
@Component
@Slf4j
public class Query {

    @Autowired
    private WebClient tikuWebClient;


    /**
     * 同步查询题目返回答案
     *
     * @param question 题目信息
     * @return 答案
     * @throws IOException 异常
     */
    @Autowired
    private DeepSeekClient deepSeekClient;
    @Autowired private LearningTongAiClient learningTongAi;

    @Autowired
    private LiveProgressTracker liveProgress;

    public String queryAnswerSync(Question question) throws IOException {
        liveProgress.checkCancelled();liveProgress.answerSource("题库查询中");
        String answer = null;
        try { answer = queryBankAnswer(question); }
        catch (RuntimeException ex) {
            liveProgress.checkCancelled();
            if (!deepSeekClient.isEnabled() && (learningTongAi==null||!learningTongAi.isEnabled())) throw ex;
            log.warn("Question bank unavailable; trying configured AI providers");
        }
        liveProgress.checkCancelled();
        if (answer != null && !answer.trim().isEmpty()) { liveProgress.answerSource("题库已返回");return answer; }
        if(learningTongAi!=null&&learningTongAi.isEnabled()){
            liveProgress.answerSource("学习通 AI 生成中");
            try{String platform=learningTongAi.answer(question);if(platform!=null&&!platform.trim().isEmpty()){liveProgress.answerSource("学习通 AI 已返回");return platform;}}
            catch(IOException ex){liveProgress.checkCancelled();if(!deepSeekClient.isEnabled())throw new IOException("学习通AI暂不可用，请检查账号和权限");log.warn("LearningTong AI unavailable; trying configured DeepSeek fallback");}
        }
        if (deepSeekClient.isEnabled()) {
            log.info("Question bank miss; requesting DeepSeek answer");
            liveProgress.answerSource("DeepSeek 生成中");
            String result=deepSeekClient.answer(question);
            liveProgress.answerSource("DeepSeek 已返回");
            return result;
        }
        return null;
    }

    private String queryBankAnswer(Question question) throws IOException {

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("question", question.getTitle());
        requestBody.put("options", question.getTKOptions());
        int type = 0;
        String questionType = question.getType();
        switch (questionType) {
            case "single":
                type = QuestionTypeConstant.SINGLE_CHOICE;
                break;
            case "multiple":
                type = QuestionTypeConstant.MULTIPLE_CHOICE;
                break;
            case "fill":
                type = QuestionTypeConstant.FILL_IN;
                break;
            case "judgement":
                type = QuestionTypeConstant.JUDGMENT;
                break;
            case "short":
                type = QuestionTypeConstant.SHORT_ANSWER;
                break;
        }
        requestBody.put("type", type);

        return tikuWebClient.post()
                .uri("")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(raw -> {
                    try {


                        // 2. 安全反序列化
                        ObjectMapper objectMapper = new ObjectMapper();
                        QueryVO vo = objectMapper.readValue(raw, QueryVO.class);

                        if (vo == null || !Integer.valueOf(QueryResponseConstant.SUCCESS_CODE).equals(vo.getCode())) {
                            return Mono.empty();
                        }

                        return Mono.justOrEmpty(vo.getData());
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("题库结果解析错误", e));
                    } catch (Exception e) {
                        return Mono.error(e);
                    }

                })
                .timeout(Duration.ofSeconds(10))
                .block();


    }


}

