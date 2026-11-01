package com.tihai.controller;
import com.tihai.utils.AiSettingsService;
import com.tihai.common.R;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/local/ai")
public class AiSettingsController {
    private final AiSettingsService settings;
    @org.springframework.beans.factory.annotation.Autowired private com.tihai.utils.LearningTongAiClient learningTong;
    public AiSettingsController(AiSettingsService settings){this.settings=settings;}
    @GetMapping("/config") public R current() throws java.io.IOException {return R.ok().setData(settings.current());}
    @PostMapping(value="/config",consumes="application/json") public R save(@RequestBody Map<String,Object> body){try{
        return R.ok("AI 配置已保存，立即生效").setData(settings.save((String)body.get("apiKey"),(String)body.get("model"),Boolean.TRUE.equals(body.get("enabled")),body.get("visionFallback") instanceof Boolean?(Boolean)body.get("visionFallback"):null,body.get("learningTongEnabled") instanceof Boolean?(Boolean)body.get("learningTongEnabled"):null));
    }catch(IllegalArgumentException e){return R.error(400,e.getMessage());}catch(java.io.IOException e){return R.error(500,"配置保存失败，请检查本机目录权限");}}
    @PostMapping(value="/test",consumes="application/json") public R test(@RequestBody Map<String,Object> body){try{
        return R.ok("连接成功，密钥和所选模型可用").setData(settings.test((String)body.get("apiKey"),(String)body.get("model")));
    }catch(IllegalArgumentException e){return R.error(400,e.getMessage());}catch(java.io.IOException e){return R.error(502,"连接验证失败，请检查密钥、网络或模型权限");}}
    @PostMapping(value="/learningtong-test",consumes="application/json") public R testLearningTong(@RequestBody Map<String,Object> body){try{return R.ok("学习通AI连接成功，测试题返回了可核验答案").setData(learningTong.test());}catch(java.io.IOException e){return R.error(502,"学习通AI暂不可用，请先保存开关、重新查询课程登录，或检查账号权限及额度");}}
}
