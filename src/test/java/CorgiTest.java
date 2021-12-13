import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.corgi.common.util.JWTUtils;
import com.corgi.common.util.RequestUtil;
import org.junit.Test;
import org.springframework.util.DigestUtils;

import java.util.StringJoiner;

public class CorgiTest {
    @Test
    public void testJWT() {
        System.out.println(JWTUtils.createJWT("-1", "v0.0"));
    }

    @Test
    public void testJWTPayload() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3MzU0MjUwNDgsInVzZXJJZCI6Ii0xIiwidmVyc2lvbiI6IlYxLjAiLCJpYXQiOjE1ODgzMDIxNTV9.UcR_5JX0hIPBzySF9q4vh4oI8DyY7pXmHTzQqffiKkM";
        System.out.println(JWTUtils.createJWT("8", "1.8.0", 1000 * 60 * 60 * 24 * 365 * 100L));
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
        String jwt = "2";
        //DecodedJWT decodedJWT = JWTUtils.decodeToken(jwt);
        //System.out.println(decodedJWT.getClaim("user_id").asString());
        String old = DigestUtils.md5DigestAsHex(jwt.getBytes());
        String high1 = old.substring(0, 8);
        String high2 = old.substring(8, 16);
        String low1 = old.substring(16, 24);
        String low2 = old.substring(24, 32);
        Long new1 = Long.parseLong(high1, 16) ^ Long.parseLong(high2, 16) ^ Long.parseLong(low1, 16) ^ Long.parseLong(low2, 16);
        System.out.println(new1 & (1 << 23) - 1);
    }

    @Test
    public void testJoin() {
        String teamCodes = "01,02";
        StringJoiner sj = new StringJoiner("','");
        String[] codes = teamCodes.split(",");
        for (int i = 0; i < codes.length; i++) {
            try {
                if (Integer.valueOf(codes[i]) > 0) {
                    sj.add(codes[i]);
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        System.out.println(sj.toString());
    }
}
