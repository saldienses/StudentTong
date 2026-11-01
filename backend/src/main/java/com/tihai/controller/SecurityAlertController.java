package com.tihai.controller;
import org.springframework.web.bind.annotation.*;import org.springframework.beans.factory.annotation.Autowired;import com.tihai.utils.*;import com.tihai.common.R;
@RestController @RequestMapping("/local/security") public class SecurityAlertController {
 @Autowired SecurityAlertHub alerts;
 @org.springframework.beans.factory.annotation.Autowired private AccountRegistry registry;@GetMapping("/alerts") public R list(){java.util.List<java.util.Map<String,Object>> rows=alerts.snapshot();rows.removeIf(r->{String m=String.valueOf(r.get("module")),id=String.valueOf(r.get("task"));return "yuketang".equals(m)?!id.startsWith(AccountScope.id()+":"):("chapters".equals(m)?!registry.ownsTask(id):!registry.ownsResource("exams".equals(m)?"exam":m,id));});return R.ok().setData(rows);}
}
