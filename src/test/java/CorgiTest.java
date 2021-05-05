import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.corgi.common.util.JWTUtils;
import com.corgi.common.util.RequestUtil;
import org.junit.Test;

public class CorgiTest {
    @Test
    public void testJWT() {
        System.out.println(JWTUtils.createJWT("-1", "v0.0"));
    }

    @Test
    public void testJWTPayload() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3MzU0MjUwNDgsInVzZXJJZCI6Ii0xIiwidmVyc2lvbiI6IlYxLjAiLCJpYXQiOjE1ODgzMDIxNTV9.UcR_5JX0hIPBzySF9q4vh4oI8DyY7pXmHTzQqffiKkM";
        System.out.println(JWTUtils.createJWT("7", "1.7.0", 1000 * 60 * 60 * 24 * 365 * 100L));
        DecodedJWT decodedJWT = JWTUtils.decodeToken(jwt);
        System.out.println(decodedJWT.getClaim("userId").asString());
        System.out.println(decodedJWT.getClaim("version").asString());
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
        String test = "https://outin-b05cd5a4435f11ebb03200163e1a3b4a.oss-cn-shanghai.aliyuncs.com/customerTrans/2ae7e3316109dcaf0598af101336d5e0/126a8e06-17916f1ca6c-0006-cca8-bf1-60efa.mp4?Expires=1619591217&OSSAccessKeyId=LTAIrkwb21KyGjJl&Signature=fTaY2439DEkP3JT963wNn6cCVIg%3D";
        System.out.printf(test.split("\\?Expires")[0]);
//        if (!test.contains(" ")) {
//            System.out.println(test.substring(0, 10) + " " + test.substring(10)+ Double.valueOf("70.00"));
//        }
    }
}
