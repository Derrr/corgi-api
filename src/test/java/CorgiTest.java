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
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE1ODY2MTA4ODcsInVzZXJJZCI6IjEiLCJ2ZXJzaW9uIjoiaW9zLXYxLjciLCJpYXQiOjE1ODY2MDM2ODd9.4_SMnB3ZlgNdXMGbYoB2OqtOGHPP78LdZyKWHlpyeds";
        DecodedJWT decodedJWT = JWTUtils.decodeToken(jwt);
        System.out.println(decodedJWT.getClaim("userId").asString());
        System.out.println(decodedJWT.getClaim("version").asString());
    }

    @Test
    public void test() {
        String test = "2020/05/0115:00";
        if(!test.contains(" ")){
            System.out.println(test.substring(0, 10)+" "+test.substring(10));
        }
    }
}
