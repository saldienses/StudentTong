package com.tihai.utils;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import okhttp3.*;
import static org.junit.jupiter.api.Assertions.*;
class AiSettingsServiceTest {
    @Test void learningTongCanBeEnabledIndependentlyAndPersists()throws Exception{AiSettingsService s=new AiSettingsService(file(),new OkHttpClient());s.save("","deepseek-flash",false,false,true);assertEquals(true,s.current().get("learningTongEnabled"));assertEquals(false,s.current().get("enabled"));s.save("","deepseek-flash",false);assertEquals(true,s.current().get("learningTongEnabled"));}

    @Test void visualPreferenceCanBeDisabledAndSurvivesOrdinarySave()throws Exception{
        AiSettingsService s=new AiSettingsService(file(),new OkHttpClient());s.save("sk-local-unit-test-key","deepseek-flash",true,false);assertEquals(false,s.current().get("visionFallback"));s.save("","deepseek-v4-pro",true);assertEquals(false,s.current().get("visionFallback"));s.save("","deepseek-v4-pro",true,true);assertEquals(true,s.current().get("visionFallback"));assertEquals("deepseek-flash",s.current().get("visionModel"));
    }
    private Path file() throws Exception{Path parent=Paths.get("target","ai-settings-tests");Files.createDirectories(parent);return Files.createTempDirectory(parent,"case-").resolve("deepseek.properties");}
    @Test void blankKeyPreservesSecretAndMetadataNeverEchoesIt() throws Exception{
        AiSettingsService s=new AiSettingsService(file(),new OkHttpClient());String key="sk-local-unit-test-secret";
        Map<String,Object> result=s.save(key,"deepseek-flash",true);assertFalse(result.toString().contains(key));assertEquals(true,result.get("configured"));
        s.save("","deepseek-v4-pro",false);assertEquals(false,s.current().get("enabled"));assertEquals(true,s.current().get("configured"));assertFalse(s.current().containsKey("api-key"));
    }
    @Test void officialConnectionTestUsesModelsAndDoesNotSaveNewKey() throws Exception{
        OkHttpClient http=new OkHttpClient.Builder().addInterceptor(chain->{assertEquals("api.deepseek.com",chain.request().url().host());assertEquals("/models",chain.request().url().encodedPath());assertEquals("Bearer sk-local-unit-test-key",chain.request().header("Authorization"));return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create("{\"data\":[{\"id\":\"deepseek-flash\"}]}",MediaType.parse("application/json"))).build();}).build();
        AiSettingsService s=new AiSettingsService(file(),http);assertEquals(true,s.test("sk-local-unit-test-key","deepseek-flash").get("connected"));assertEquals(false,s.current().get("configured"));
    }
    @Test void rejectsUnsupportedModelsMissingKeyAndHeaderInjection() throws Exception{
        AiSettingsService s=new AiSettingsService(file(),new OkHttpClient());assertThrows(IllegalArgumentException.class,()->s.save("","deepseek-flash",true));assertThrows(IllegalArgumentException.class,()->s.save("sk-local-unit-test-key","other",true));assertThrows(IllegalArgumentException.class,()->s.save("sk-local-key\r\nInvalid: header","deepseek-flash",true));
    }
}
