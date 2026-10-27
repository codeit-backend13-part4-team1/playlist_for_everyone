import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageBadgeTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @CsvSource({
            "80, 20, 80.00%, #4c1",
            "79, 21, 79.00%, #e05d44",
            "79999, 20001, 79.99%, #e05d44"
    })
    void 커버리지에_맞는_수치와_색상으로_배지를_생성한다(
            int covered,
            int missed,
            String expectedPercentage,
            String expectedColor
    ) throws Exception {
        // 준비: 테스트용 XML 파일 생성
        Path reportPath = tempDir.resolve("report.xml");
        Path badgePath = tempDir.resolve("coverage.svg");

        String xml = """
                <report>
                    <counter type="LINE" covered="%d" missed="%d"/>
                </report>
                """.formatted(covered, missed);

        Files.writeString(reportPath, xml);

        // 실행: 배지 생성 프로그램 호출
        CoverageBadge.main(new String[]{
                reportPath.toString(),
                badgePath.toString()
        });

        // 확인: 생성된 배지의 수치와 색상 검사
        String svg = Files.readString(badgePath);

        assertTrue(svg.contains(expectedPercentage));
        assertTrue(svg.contains(expectedColor));
    }
}