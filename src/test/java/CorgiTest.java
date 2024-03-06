
import com.alibaba.fastjson.JSONObject;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.corgi.common.util.JWTUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.springframework.util.DigestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;

import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.interfaces.ECPublicKey;

import java.util.Base64;
import java.util.Optional;

public class CorgiTest {
    @Test
    public void testJWT() {
        System.out.println(JWTUtils.createJWT("-1", "v0.0"));
    }

    @Test
    public void testJWTPayload() {
        String jwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ii1LSTNROW5OUjdiUm9meG1lWm9YcWJIWkdldyJ9.eyJhdWQiOiJjODI1YmNkZC0wMjJjLTRlNTQtYThmYS04ZWRiNjVkNjU2NGMiLCJpc3MiOiJodHRwczovL2xvZ2luLm1pY3Jvc29mdG9ubGluZS5jb20vZTUyMzQ1MGItMzJmNC00MWQ5LTlmN2UtMTQyNDA1NzllYmM5L3YyLjAiLCJpYXQiOjE2OTM1MzY2OTIsIm5iZiI6MTY5MzUzNjY5MiwiZXhwIjoxNjkzNTQwNTkyLCJuYW1lIjoi5YiYIOazsOeEtiIsIm5vbmNlIjoiNjQ4ZjcwMmYtMGE2My00YWZmLTg3YTAtMjg5NWNiOWU0MDJmIiwib2lkIjoiY2E0ZDY0NzQtZjNkYi00MDdmLWFiMzQtY2U3NGIyNjA5ZDUzIiwicHJlZmVycmVkX3VzZXJuYW1lIjoidHJsaXVAaGlsbGluc2lnaHQuY29tIiwicmgiOiIwLkFUNEFDMFVqNWZReTJVR2ZmaFFrQlhucnlkMjhKY2dzQWxST3FQcU8yMlhXVmt3LUFORS4iLCJzdWIiOiJjaXFuRnAtdHNLSzdUeDYzM2M3WW5WTHlidm1mWjkxMjZiS0hieG04LWRJIiwidGlkIjoiZTUyMzQ1MGItMzJmNC00MWQ5LTlmN2UtMTQyNDA1NzllYmM5IiwidXRpIjoiaDEzOXJFaEhERUdJZDE2V25fSmdBQSIsInZlciI6IjIuMCJ9.Uy_LCvnGXaq5vHkVLsWUA9Md1okoRQmmTm1N64CHHmTZi0T2C2pO5UlHFxQ9cWTz5xV2zVF-mJNah3uJcV2nhCU5Iq4jsjZH5yNUnbkz42GywPyZ9-MSnBH4DhWsvPrfhMvlXT13QaRP5B_xY45FrNkzabAPty8w5R0nh4h4mue8MCiELQ_vBnxuWg6SotuwHzCVZ5IE2GvbTuXFicXA9g5UILw7gybmwklLBQY3FFthWWupvOdHYVUISvrbJHqyTJTIQMicp-ASmJM4Fvh6FDLPbAWqmePiZGFk2olkOh8eYIygHEFY4-c6Oz8xR0AKPPZUCdpPSYH3Tjxc1-EsFA";        //System.out.println(JWTUtils.createJWT("8", "1.8.0", 1000 * 60 * 60 * 24 * 365 * 100L));
        DecodedJWT decodedJWT = JWTUtils.decodeToken(jwt);
        System.out.println(decodedJWT.getClaims());
        System.out.println(decodedJWT.getExpiresAt());
    }

    @Test
    public void testProjectCJWTPayload() {
        String jwt = JWTUtils.createJWT("-2", "1.8.0", 1000 * 60 * 60 * 24 * 365 * 100L);
        System.out.println(jwt);
        DecodedJWT decodedJWT = JWTUtils.decodeToken(jwt);
        System.out.println(decodedJWT.getExpiresAt());
        System.out.println(decodedJWT.getClaim("version").asString());
    }

    @Test
    public void test() {
        String objStr = "{\"content\":[{\"columnIds\":[\"1qaz\",\"2wsx\"],\"data\":[{\"1qaz\":{\"isBold\":false,\"type\":\"text\",\"value\":\"This review process is effective in terms of helping me evaluate my team members and develop talents\",\"width\":770},\"2wsx\":{\"dataRange\":{\"maximum\":5,\"minimum\":1},\"isBold\":false,\"type\":\"input_num\",\"value\":\"\",\"width\":100}},{\"1qaz\":{\"isBold\":false,\"type\":\"text\",\"value\":\"The review process allows me to give objective assessment of each employee’s capabilities and potential\",\"width\":770},\"2wsx\":{\"dataRange\":{\"maximum\":5,\"minimum\":1},\"is_bold\":false,\"type\":\"input_num\",\"value\":\"\",\"width\":100}},{\"1qaz\":{\"isBold\":false,\"type\":\"text\",\"value\":\"I conduct productive conversations with team members and review contributes to my team management effort\",\"width\":770},\"2wsx\":{\"dataRange\":{\"maximum\":5,\"minimum\":1},\"isBold\":false,\"type\":\"input_num\",\"value\":\"\",\"width\":100}},{\"1qaz\":{\"isBold\":false,\"type\":\"text\",\"value\":\"The review process is clear and efficient\",\"width\":770},\"2wsx\":{\"dataRange\":{\"maximum\":5,\"minimum\":1},\"isBold\":false,\"type\":\"input_num\",\"value\":\"\",\"width\":100}}],\"display\":{\"1qaz\":{\"type\":\"text\",\"value\":\"\",\"width\":660,\"widthMob\":0,\"dataRange\":null,\"isBold\":false},\"2wsx\":{\"type\":\"input_num\",\"value\":\"\",\"width\":240,\"widthMob\":0,\"dataRange\":{\"maximum\":5,\"minimum\":1},\"isBold\":false}},\"groupId\":\"\",\"groupName\":\"\",\"header\":{\"1qaz\":{\"name\":\"Item\",\"type\":\"text\",\"width\":780},\"2wsx\":{\"name\":\"Rating\",\"type\":\"text\",\"width\":150}},\"isNeedAdd\":0,\"maximum\":0,\"minimum\":0,\"questionId\":\"table12\"}],\"dataRange\":\"\",\"guidance\":\"\",\"icon\":\"\",\"id\":997,\"instructions\":\"<p>We highly appreciate your participation in the Mid-year Appraisal and your effort in talent development. Please provide your feedback to this appraisal in the questions below.</p><br/><p style='color: #666; line-height: 19px;font-weight: normal;margin-top: 3px;'>1 - Strongly disagree<br/>2 - Disagree<br/>3 - Neutral<br/>4 - Agree<br/>5 - Strongly agree</p>\",\"isDisplayGroupName\":1,\"isMust\":1,\"note\":\"\",\"questionId\":\"table1\",\"questionType\":3,\"tips\":\"\",\"title\":\"\",\"showTips\":false}\n";
        JSONObject obj = JSONObject.parseObject(objStr);
        System.out.println(obj);
    }

    @Test
    public void testJoin() {
        System.out.println("https://corgi-pic.oss-cn-beijing.aliyuncs.com/avatar/946676/1709700344750".replaceAll("corgi-pic\\.oss-cn-beijing\\.aliyuncs\\.com", "image.corgi.org.cn"));
    }
}