package com.tihai.utils;


import com.tihai.common.*;
import com.tihai.dubbo.pojo.course.Course;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Copyright : DuanInnovator
 * @Description : 课程解码
 * @Author : DuanInnovator
 * @CreateTime : 2025/2/26
 * @Link : <a href="https://github.com/DuanInnovator/SuperAutotudy">...</a>
 **/
public class Decode {
    /**
     * 解码课程列表
     */
    public static List<Course> decodeCourseList(String html) {
//        LoggerUtil.logger.trace("开始解码课程列表...");
        List<Course> courseList = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements rawCourses = doc.select("div.course");
        Pattern cpiPattern = Pattern.compile("cpi=(.*?)&");

        for (Element course : rawCourses) {
            // 排除未开放的课程：含有 a.not-open-tip 或 div.not-open-tip 的忽略
            if (!course.select("a.not-open-tip").isEmpty() || !course.select("div.not-open-tip").isEmpty()) {
                continue;
            }
            Course courseDetail = new Course();
            courseDetail.setId(course.attr("id"));
            courseDetail.setInfo(course.attr("info"));
            courseDetail.setRoleId(course.attr("roleid"));
            courseDetail.setClazzId(course.select("input.clazzId").attr("value"));
            courseDetail.setCourseId(course.select("input.courseId").attr("value"));

            // 从 a 标签的 href 中提取 cpi
            String href = course.select("a").attr("href");
            Matcher m = cpiPattern.matcher(href);
            if (m.find()) {
                courseDetail.setCpi(m.group(1));
            } else {
                courseDetail.setCpi("");
            }
            courseDetail.setTitle(course.select("span.course-name").attr("title"));
            Element descEl = course.select("p.margint10").first();
            courseDetail.setDesc(descEl != null ? descEl.attr("title") : "");
            courseDetail.setTeacher(course.select("p.color3").attr("title"));
            courseList.add(courseDetail);
        }
        return courseList;
    }

    /**
     * 解码二级课程列表（文件夹）
     */
    public static List<CourseFolder> decodeCourseFolder(String html) {
//        LoggerUtil.logger.trace("开始解码二级课程列表...");
        List<CourseFolder> folderList = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements rawFolders = doc.select("ul.file-list > li");
        for (Element folder : rawFolders) {
            String fileId = folder.attr("fileid");
            if (fileId != null && !fileId.isEmpty()) {
                CourseFolder courseFolder = new CourseFolder();
                courseFolder.setId(fileId);
                courseFolder.setRename(folder.select("input.rename-input").attr("value"));
                folderList.add(courseFolder);
            }
        }
        return folderList;
    }

    /**
     * 解码章节列表（课程点）
     */
    public static CoursePoint decodeCoursePoint(String html) {
//        LoggerUtil.logger.trace("开始解码章节列表...");
        CoursePoint coursePoint = new CoursePoint();
        coursePoint.setHasLocked(false);
        List<ChapterPoint> points = new ArrayList<>();

        Document doc = Jsoup.parse(html);
        Elements chapterUnits = doc.select("div.chapter_unit");
        Pattern idPattern = Pattern.compile("^cur(\\d{1,20})$");

        for (Element chapter : chapterUnits) {
            Elements rawPoints = chapter.select("li");
            for (Element li : rawPoints) {
                Element pointDiv = li.select("div").first();
                if (pointDiv == null || !pointDiv.hasAttr("id")) {
                    continue;
                }
                Matcher m = idPattern.matcher(pointDiv.attr("id"));
                if (!m.find()) {
                    continue;
                }
                ChapterPoint pointDetail = new ChapterPoint();
                pointDetail.setId(m.group(1));
                String title = pointDiv.select("a.clicktitle").text().replace("\n", "").trim();
                pointDetail.setTitle(title);
                // 默认为1
                pointDetail.setJobCount(1);
                Element knowledgeJobCount = pointDiv.select("input.knowledgeJobCount").first();
                if (knowledgeJobCount != null) {
                    pointDetail.setJobCount(Integer.parseInt(knowledgeJobCount.attr("value")));
                } else {
                    Element tipEl = pointDiv.select("span.bntHoverTips").first();
                    if (tipEl != null && tipEl.text().contains("解锁")) {
                        coursePoint.setHasLocked(true);
                    }
                }
                points.add(pointDetail);
            }
        }
        coursePoint.setPoints(points);
        return coursePoint;
    }

    /**
     * 解码任务点列表
     * 返回 Pair(任务点列表, 任务附加信息)
     */
    public static Pair<List<Job>,JobInfo> decodeCourseCard(String html){return CourseCardDecoder.decode(html);}

    /**
     * 解码题目和答案表单信息
     */
    //TODO 待优化
    public static Map<String, Object> decodeQuestionsInfo(String htmlContent) throws Exception {
        return QuizPageReader.read(htmlContent);
    }

    /**
     * 辅助方法：替换字符串中的 \r、\t、\n
     */
    private static String replaceRtn(String text) {
        if (text == null) return "";
        return text.replace("\r", "").replace("\t", "").replace("\n", "");
    }
}

