package com.tihai.utils;
import com.tihai.common.Question;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryFallbackTest {
    private Query query(String bankJson, DeepSeekClient deepSeek) {
        WebClient web=WebClient.builder().exchangeFunction(request->Mono.just(ClientResponse.create(HttpStatus.OK)
            .header("Content-Type",MediaType.APPLICATION_JSON_VALUE).body(bankJson).build())).build();
        Query q=new Query();ReflectionTestUtils.setField(q,"liveProgress",new LiveProgressTracker());ReflectionTestUtils.setField(q,"tikuWebClient",web);ReflectionTestUtils.setField(q,"deepSeekClient",deepSeek);return q;
    }
    private Question question() { Question q=new Question();q.setType("single");q.setTitle("Local test");return q; }
    @Test void bankAnswerDoesNotInvokePaidModel() throws Exception {
        DeepSeekClient ai=mock(DeepSeekClient.class);
        assertEquals("Bank answer",query("{\"code\":200,\"data\":\"Bank answer\"}",ai).queryAnswerSync(question()));
        verifyNoInteractions(ai);
    }
    @Test void bankMissAndMalformedResponseUseEnabledFallback() throws Exception {
        DeepSeekClient ai=mock(DeepSeekClient.class);when(ai.isEnabled()).thenReturn(true);when(ai.answer(any())).thenReturn("AI answer");
        assertEquals("AI answer",query("{\"code\":404}",ai).queryAnswerSync(question()));
        assertEquals("AI answer",query("not-json",ai).queryAnswerSync(question()));
        verify(ai,times(2)).answer(any());
    }
    @Test void missingKeyDoesNotInvokeModelAndModelFailuresPropagate() throws Exception {
        DeepSeekClient ai=mock(DeepSeekClient.class);when(ai.isEnabled()).thenReturn(false);
        assertNull(query("{\"code\":404}",ai).queryAnswerSync(question()));verify(ai,never()).answer(any());
        when(ai.isEnabled()).thenReturn(true);when(ai.answer(any())).thenThrow(new IOException("DeepSeek HTTP 401"));
        assertThrows(IOException.class,()->query("{\"code\":404}",ai).queryAnswerSync(question()));
    }
}
