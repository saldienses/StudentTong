package com.tihai.controller;
import com.tihai.common.R;import com.tihai.utils.*;import org.springframework.web.bind.annotation.*;import org.springframework.beans.factory.annotation.Autowired;import java.util.*;
@RestController @RequestMapping("/local/workspace") public class WorkspaceController {
 @Autowired AccountRegistry registry;
 @GetMapping("/accounts")public R accounts()throws Exception{return R.ok().setData(registry.view());}
 @PostMapping("/select")public R select(@RequestBody Map<String,String> b){try{registry.refresh();registry.select(b.get("platform"),b.getOrDefault("accountId",""));return R.ok("已切换；各账号已运行的任务会继续独立执行").setData(registry.view());}catch(Exception e){return R.error(400,e.getMessage());}}
}
