package com.tihai.utils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tihai.common.Question;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class DeepSeekClientTest {
    private final ObjectMapper json=new ObjectMapper();
    private Question question(String type) {
        Question q=new Question();q.setType(type);q.setTitle("A local test");q.setTKOptions(Arrays.asList("A. Alpha","B. Beta","C. Gamma"));return q;
    }
    @Test void mapsChoiceLettersToExactOptionTexts() throws Exception {
        assertEquals("B. Beta",DeepSeekClient.normalize(question("single"),json.readTree("[\"B\"]")));
        assertEquals("A. Alpha#C. Gamma",DeepSeekClient.normalize(question("multiple"),json.readTree("[\"C\",\"A\",\"A\"]")));
    }
    @Test void rejectsUnknownOptionsAndEmptyAnswers() throws Exception {
        assertThrows(IOException.class,()->DeepSeekClient.normalize(question("single"),json.readTree("[\"Z\"]")));
        assertThrows(IOException.class,()->DeepSeekClient.normalize(question("single"),json.readTree("[\"A\",\"B\"]")));
        assertThrows(IOException.class,()->DeepSeekClient.normalize(question("single"),json.readTree("[]")));
    }
    @Test void mapsJudgementAndWrittenAnswers() throws Exception {
        assertEquals("正确",DeepSeekClient.normalize(question("judgement"),json.readTree("[\"true\"]")));
        assertEquals("错误",DeepSeekClient.normalize(question("judgement"),json.readTree("[\"false\"]")));
        assertThrows(IOException.class,()->DeepSeekClient.normalize(question("judgement"),json.readTree("[\"maybe\"]")));
        assertEquals("one#two",DeepSeekClient.normalize(question("fill"),json.readTree("[\"one\",\"two\"]")));
        assertEquals("A written answer",DeepSeekClient.normalize(question("short"),json.readTree("[\"A written answer\"]")));
    }
}
