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
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MTk1NDA4OTAsInVzZXJJZCI6IjE5ODg2NiIsInZlcnNpb24iOiIxLjguMCIsImlhdCI6MTYxOTUzMzY5MH0.NIpXq5sL7QP9h_rwwKMqxeX9OMhTKI4Pap7XW_6hZ7w";
        System.out.println(JWTUtils.createJWT("7", "1.8.0", 1000 * 60 * 60 * 24 * 365 * 100L));
        DecodedJWT decodedJWT = JWTUtils.decodeToken(jwt);
        System.out.println(decodedJWT.getClaim("userId").asString());
        System.out.println(decodedJWT.getClaim("version").asString());
    }

    @Test
    public void testProjectCJWTPayload() {
        String jwt = JWTUtils.createJWT("-2", "V1.0", 1000 * 60 * 60 * 24 * 365 * 100L);
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
