import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.corgi.common.util.JWTUtils;
import com.corgi.common.util.RequestUtil;
import org.junit.Test;

import java.util.StringJoiner;

public class CorgiTest {
    @Test
    public void testJWT() {
        System.out.println(JWTUtils.createJWT("-1", "v0.0"));
    }

    @Test
    public void testJWTPayload() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3MzU0MjUwNDgsInVzZXJJZCI6Ii0xIiwidmVyc2lvbiI6IlYxLjAiLCJpYXQiOjE1ODgzMDIxNTV9.UcR_5JX0hIPBzySF9q4vh4oI8DyY7pXmHTzQqffiKkM";
        System.out.println(JWTUtils.createJWT("4", "1.8.0", 1000 * 60 * 60 * 24 * 365 * 100L));
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
        String jwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJXbnI2UFFPNCIsImlhdCI6MTYyMDk2MTMwNCwiZXhwIjoxNjIwOTY4NTA0LCJhdWQiOiI1azAzZDlyZXc3Iiwib3Blbl9pZCI6IjVkenZuMHB3NTVqMzJwMXgiLCJ1c2VyX2lkIjoiNWR6dm4wcHc1NWozMnAxeCIsIm5hbWUiOiJcdTkwZWRcdTRlMDBcdTY2MGUiLCJhdmF0YXIiOiJodHRwczovL3VzaHUub3NzLWNuLWJlaWppbmcuYWxpeXVuY3MuY29tL1ducjZQUU80L2F2YXRhci82YTg3ZjFmYTA4ZjA2YWE2MzQ5Y2EyMzE5NjY1MWQ1Ny5qcGVnIiwidmVyc2lvbiI6InYxIn0.AMQenDoqbiaRJDbYltqy3urat3kG1FzobqlZAPndk17Vusc7JEaYZP8R2h2YBocywWm634sKBsfyXgeHJdZtBf4e91_toaBHc2rX-NoWHwar9_pD2CizBGp9YEosU4RXyNLJEZ4n8pukp5lgvWraSC4LhO-i0yoveHLcGER2FrvX0Q-ihEBlaR0tfhzpWrMomvAgCH3cFs5UI5OzGnxBMxDj0OR9QEKi2Z3CNOJ9xu-1pnpwXSzhXr9OjzubNtbTot0cqCNCuAfaP1YIrmV4dnaCN9z4pvd0nQfKye3DO44Vm5GnIRgc_sFFU9Co_hITHTpOwOx18VNiS4nLLoqZ1A";
        DecodedJWT decodedJWT = JWTUtils.decodeToken(jwt);
        System.out.println(decodedJWT.getClaim("user_id").asString());
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
