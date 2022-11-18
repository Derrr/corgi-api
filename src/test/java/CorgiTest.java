
import com.alibaba.fastjson.JSONObject;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.corgi.common.util.JWTUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
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
        String jwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE2NjgyNDEzNDUsInVzZXJJZCI6IjEzIiwidmVyc2lvbiI6IjIuMi42IiwiaWF0IjoxNjY4MjM0MTQ1fQ.oMQDjJUo8tRHbOFZujmFBOVi1eZbyuIj-5iF4qAjCPI";
        //System.out.println(JWTUtils.createJWT("8", "1.8.0", 1000 * 60 * 60 * 24 * 365 * 100L));
        DecodedJWT decodedJWT = JWTUtils.decodeToken(jwt);
        System.out.println(decodedJWT.getClaim("userId").asString());
        System.out.println(decodedJWT.getClaim("version").asString());
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
        System.out.println("aaaa#a".split("#")[1]);
    }

    @Test
    public void appStoreResponse() throws CertificateException {
        String jwt = "eyJleHAiOjE2NjgyNDEzNDUsInVzZXJJZCI6IjEzIiwidmVyc2lvbiI6IjIuMi42IiwiaWF0IjoxNjY4MjM0MTQ1fQ";
        System.out.println(Base64.getDecoder().decode(jwt));
    }
}