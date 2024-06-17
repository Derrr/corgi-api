
import com.auth0.jwt.interfaces.DecodedJWT;
import com.corgi.common.util.JWTUtils;
import org.bouncycastle.pqc.math.linearalgebra.Matrix;
import org.junit.Test;

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
        int scale = 19;
        double[][] m = new double[scale][scale];
        for (int i = 0; i < scale - 1; i++) {
            m[i][i + 1] = 1.0;
        }
        for (int i = 0; i < scale; i++) {
            m[scale - 1][i] = 1.0 / scale;
        }
        double[][] result = this.multiply(m, m, scale);
        for (int i = 0; i < 100; i++) {
            System.out.println(result[scale - 1][scale - 1]);
            result = this.multiply(result, m, scale);
        }
        System.out.println(result[scale - 1][scale - 1]);
    }

    private double[][] multiply(double[][] m1, double[][] m2, int scale) {
        double[][] result = new double[scale][scale];
        for (int i = 0; i < scale; i++) {
            for (int j = 0; j < scale; j++) {
                for (int l = 0; l < scale; l++) {
                    result[i][j] += m1[i][l] * m2[l][j];
                }
            }
        }
        return result;
    }

    @Test
    public void testJoin() {
        System.out.println("https://corgi-pic.oss-cn-beijing.aliyuncs.com/avatar/946676/1709700344750".replaceAll("corgi-pic\\.oss-cn-beijing\\.aliyuncs\\.com", "image.corgi.org.cn"));
    }
}