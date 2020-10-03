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
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjQ0NTIzMDkwNywidXNlcklkIjoiLTEiLCJ2ZXJzaW9uIjoidjAuMCIsImlhdCI6MTU4NjU5ODIwM30.MkI7HCXzDneLoyeULVTdKkJJ3WuKrKs4AlRQHk11TKE";
        System.out.println(JWTUtils.createJWT("-1", "V1.0", 1000 * 60 * 60 * 24 * 365 * 100L));
        DecodedJWT decodedJWT = JWTUtils.decodeToken(jwt);
        System.out.println(decodedJWT.getExpiresAt());
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
        String test = "2020/05/0115:00";
        if (!test.contains(" ")) {
            System.out.println(test.substring(0, 10) + " " + test.substring(10)+ Double.valueOf("70.00"));
        }
    }
}
