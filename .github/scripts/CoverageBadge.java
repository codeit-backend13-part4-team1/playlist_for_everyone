import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Element;
import java.nio.file.Path;


public class CoverageBadge {

    /** JaCoCo XML의 전체 라인 커버리지를 읽어 SVG 배지를 생성합니다. */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "JaCoCo XML 경로와 배지 저장 경로가 필요합니다."
            );
        }

        Path reportPath = Path.of(args[0]);
        Path badgePath = Path.of(args[1]);

// XML 파일을 읽을 준비
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

// XML에 적힌 외부 파일을 추가로 읽지 않도록 설정
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd", false
        );
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false
        );
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities", false
        );

        var document = factory.newDocumentBuilder().parse(reportPath.toFile());

// 개별 클래스가 아닌 프로젝트 전체의 LINE 결과 선택
        var xpath = XPathFactory.newInstance().newXPath();
        Element lineCounter = (Element) xpath.evaluate(
                "/report/counter[@type='LINE']",
                document,
                javax.xml.xpath.XPathConstants.NODE
        );

        if (lineCounter == null) {
            throw new IllegalStateException("전체 라인 커버리지 결과가 없습니다.");
        }

        int covered = Integer.parseInt(lineCounter.getAttribute("covered"));
        int missed = Integer.parseInt(lineCounter.getAttribute("missed"));

        System.out.println("테스트에서 실행된 줄: " + covered);
        System.out.println("실행되지 않은 줄: " + missed);

        int total = covered + missed;

        if (total == 0) {
            throw new IllegalStateException("커버리지를 계산할 코드가 없습니다.");
        }

        double coverage = (double) covered / total * 100;

        System.out.printf("전체 라인 커버리지: %.2f%%%n", coverage);

        // 79.999%가 80.00%로 표시되지 않도록 소수 둘째 자리까지 버림
        String percentage = BigDecimal.valueOf(covered)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.DOWN)
                .toPlainString() + "%";

// 실제 비율이 80% 이상이면 초록색, 미만이면 빨간색
        String color = covered * 100L >= total * 80L ? "#4c1" : "#e05d44";

// SVG는 글자로 작성할 수 있는 이미지 형식
        String svg = """
        <svg xmlns="http://www.w3.org/2000/svg" width="160" height="20">
          <rect width="100" height="20" fill="#555"/>
          <rect x="100" width="60" height="20" fill="%s"/>
          <g fill="white" font-family="Verdana,sans-serif"
             font-size="11" text-anchor="middle">
            <text x="50" y="14">line coverage</text>
            <text x="130" y="14">%s</text>
          </g>
        </svg>
        """.formatted(color, percentage);

// 저장할 폴더를 준비하고 이미지 파일 생성
        Files.createDirectories(badgePath.toAbsolutePath().getParent());
        Files.writeString(badgePath, svg, StandardCharsets.UTF_8);

        System.out.println("배지 생성 완료: " + badgePath);
    }

}