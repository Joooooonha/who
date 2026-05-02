import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class NecDataProbe {
    private static final String DEFAULT_OUTPUT_DIR = "data/probes";
    private static final int DEFAULT_CANDIDATE_LIMIT = 30;
    private static final int AVAILABILITY_SAMPLE_LIMIT = 3;
    private static final int PAGE_SIZE = 100;

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        if (config.showHelp) {
            printHelp();
            return;
        }

        Files.createDirectories(config.outputDir);

        if (config.fixtureMode || config.serviceKey.isBlank()) {
            if (config.serviceKey.isBlank() && !config.fixtureMode) {
                System.out.println("DATA_GO_KR_SERVICE_KEY is not set. Running fixture mode.");
            }
            ProbeRun fixtureRun = buildFixtureRun();
            ProbeReport report = ProbeReport.from(fixtureRun, "fixture", config);
            writeReports(report, config.outputDir.resolve("fixture"));
            System.out.println("Fixture report written to " + config.outputDir.resolve("fixture").toAbsolutePath());
            return;
        }

        NecOpenApiClient client = new NecOpenApiClient(config.serviceKey);
        List<ElectionCode> electionCodes = client.fetchElectionCodes();
        writeElectionCodeReports(electionCodes, config.outputDir.resolve("common-codes"));
        if (config.target.equalsIgnoreCase("pledge-availability")) {
            List<PledgeAvailability> availability = scanPledgeAvailability(client, electionCodes, config);
            writeAvailabilityReports(availability, config.outputDir.resolve("pledge-availability"));
            System.out.println("Pledge availability report written to "
                    + config.outputDir.resolve("pledge-availability").toAbsolutePath());
            return;
        }
        List<TargetSpec> targets = TargetSpec.resolve(config);

        for (TargetSpec target : targets) {
            Optional<ElectionCode> matchedCode = electionCodes.stream()
                    .filter(code -> code.sgId.equals(target.sgId) && code.sgTypecode.equals(target.sgTypecode))
                    .findFirst();

            ElectionCode election = matchedCode.orElse(new ElectionCode(
                    target.sgId,
                    target.sgTypecode,
                    target.displayName,
                    target.sgId
            ));

            System.out.println("Fetching candidates: " + target.slug + " sgId=" + target.sgId
                    + " sgTypecode=" + target.sgTypecode);
            List<Candidate> candidates;
            if (config.candidateId.isBlank()) {
                candidates = client.fetchCandidates(
                        target.sgId,
                        target.sgTypecode,
                        config.sdName,
                        config.sggName,
                        config.candidateLimit
                );
            } else {
                System.out.println("Using direct candidate id: " + config.candidateId);
                candidates = List.of(new Candidate(
                        target.sgId,
                        target.sgTypecode,
                        config.candidateId,
                        config.sdName,
                        config.sggName,
                        "",
                        "",
                        "Direct candidate " + config.candidateId,
                        ""
                ));
            }

            List<Pledge> pledges = new ArrayList<>();
            for (Candidate candidate : candidates) {
                if (candidate.candidateId.isBlank()) {
                    continue;
                }
                List<Pledge> candidatePledges = client.fetchPledges(target.sgId, target.sgTypecode, candidate.candidateId);
                for (Pledge pledge : candidatePledges) {
                    pledges.add(pledge.withCandidate(candidate));
                }
            }

            ProbeRun run = new ProbeRun(election, candidates, pledges);
            ProbeReport report = ProbeReport.from(run, "api", config);
            Path targetOutput = config.outputDir.resolve(target.slug + "-type-" + target.sgTypecode);
            writeReports(report, targetOutput);
            System.out.println("Report written to " + targetOutput.toAbsolutePath());
        }
    }

    private static ProbeRun buildFixtureRun() {
        ElectionCode election = new ElectionCode("fixture-2022-local", "3", "Fixture local election", "20220601");
        List<Candidate> candidates = List.of(
                new Candidate("fixture-2022-local", "3", "CAND-A", "서울특별시", "서울특별시", "", "무소속", "샘플 후보 A", "등록"),
                new Candidate("fixture-2022-local", "3", "CAND-B", "서울특별시", "서울특별시", "", "무소속", "샘플 후보 B", "등록"),
                new Candidate("fixture-2022-local", "3", "CAND-C", "서울특별시", "서울특별시", "", "무소속", "샘플 후보 C", "등록")
        );
        List<Pledge> pledges = List.of(
                new Pledge("CAND-A-1", "fixture-2022-local", "3", "CAND-A", "샘플 후보 A", "무소속", "서울특별시", "서울특별시", "청년", "청년 주거 지원 확대", "청년 임대주택과 전월세 상담 지원을 확대한다.", 1),
                new Pledge("CAND-A-2", "fixture-2022-local", "3", "CAND-A", "샘플 후보 A", "무소속", "서울특별시", "서울특별시", "교통", "심야 대중교통 개선", "심야 시간대 주요 노선 배차를 보강한다.", 2),
                new Pledge("CAND-B-1", "fixture-2022-local", "3", "CAND-B", "샘플 후보 B", "무소속", "서울특별시", "서울특별시", "일자리", "지역 창업 지원", "초기 창업자의 공유 사무공간과 멘토링을 지원한다.", 1),
                new Pledge("CAND-B-2", "fixture-2022-local", "3", "CAND-B", "샘플 후보 B", "무소속", "서울특별시", "서울특별시", "환경", "생활권 녹지 확대", "동네 공원과 가로수 관리 예산을 확대한다.", 2),
                new Pledge("CAND-C-1", "fixture-2022-local", "3", "CAND-C", "샘플 후보 C", "무소속", "서울특별시", "서울특별시", "복지", "1인 가구 안전망", "1인 가구 안심 귀가와 상담 서비스를 확대한다.", 1)
        );
        return new ProbeRun(election, candidates, pledges);
    }

    private static void writeReports(ProbeReport report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("validation-report.json"), report.toJson(), StandardCharsets.UTF_8);
        Files.writeString(outputDir.resolve("validation-report.md"), report.toMarkdown(), StandardCharsets.UTF_8);
    }

    private static void writeElectionCodeReports(List<ElectionCode> codes, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        JsonWriter json = new JsonWriter();
        json.beginArray();
        for (ElectionCode code : codes) {
            json.beginObject()
                    .name("sgId").value(code.sgId)
                    .name("sgTypecode").value(code.sgTypecode)
                    .name("sgName").value(code.sgName)
                    .name("sgVotedate").value(code.sgVotedate)
                    .endObject();
        }
        json.endArray();
        Files.writeString(outputDir.resolve("common-codes.json"), json.toString(), StandardCharsets.UTF_8);

        StringBuilder md = new StringBuilder();
        md.append("# NEC Common Election Codes\n\n");
        md.append("| sgId | sgTypecode | sgName | sgVotedate |\n");
        md.append("| --- | --- | --- | --- |\n");
        for (ElectionCode code : codes) {
            md.append("| ").append(escapeMarkdown(code.sgId))
                    .append(" | ").append(escapeMarkdown(code.sgTypecode))
                    .append(" | ").append(escapeMarkdown(code.sgName))
                    .append(" | ").append(escapeMarkdown(code.sgVotedate))
                    .append(" |\n");
        }
        Files.writeString(outputDir.resolve("common-codes.md"), md.toString(), StandardCharsets.UTF_8);
    }

    private static List<PledgeAvailability> scanPledgeAvailability(
            NecOpenApiClient client,
            List<ElectionCode> electionCodes,
            Config config
    ) throws IOException, InterruptedException {
        List<PledgeAvailability> rows = new ArrayList<>();
        int sampleLimit = Math.min(config.candidateLimit, AVAILABILITY_SAMPLE_LIMIT);
        for (ElectionCode code : electionCodes) {
            System.out.println("Scanning pledge availability sgId=" + code.sgId
                    + " sgTypecode=" + code.sgTypecode + " " + code.sgName);
            List<Candidate> candidates = client.fetchCandidates(
                    code.sgId,
                    code.sgTypecode,
                    config.sdName,
                    config.sggName,
                    sampleLimit
            );

            int candidatesWithPledges = 0;
            int pledgeCount = 0;
            String firstCandidateId = "";
            String firstCandidateName = "";
            String firstPledgeTitle = "";

            for (Candidate candidate : candidates) {
                if (candidate.candidateId.isBlank()) {
                    continue;
                }
                List<Pledge> pledges = client.fetchPledges(code.sgId, code.sgTypecode, candidate.candidateId);
                if (!pledges.isEmpty()) {
                    candidatesWithPledges++;
                    pledgeCount += pledges.size();
                    if (firstCandidateId.isBlank()) {
                        firstCandidateId = candidate.candidateId;
                        firstCandidateName = candidate.name;
                        firstPledgeTitle = pledges.get(0).title;
                    }
                }
            }

            rows.add(new PledgeAvailability(
                    code,
                    candidates.size(),
                    candidatesWithPledges,
                    pledgeCount,
                    firstCandidateId,
                    firstCandidateName,
                    firstPledgeTitle
            ));
        }
        return rows;
    }

    private static void writeAvailabilityReports(List<PledgeAvailability> rows, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        JsonWriter json = new JsonWriter();
        json.beginArray();
        for (PledgeAvailability row : rows) {
            json.beginObject()
                    .name("sgId").value(row.election.sgId)
                    .name("sgTypecode").value(row.election.sgTypecode)
                    .name("sgName").value(row.election.sgName)
                    .name("sgVotedate").value(row.election.sgVotedate)
                    .name("sampledCandidates").value(row.sampledCandidates)
                    .name("candidatesWithPledges").value(row.candidatesWithPledges)
                    .name("sampledPledges").value(row.sampledPledges)
                    .name("firstCandidateId").value(row.firstCandidateId)
                    .name("firstCandidateName").value(row.firstCandidateName)
                    .name("firstPledgeTitle").value(row.firstPledgeTitle)
                    .endObject();
        }
        json.endArray();
        Files.writeString(outputDir.resolve("pledge-availability.json"), json.toString(), StandardCharsets.UTF_8);

        StringBuilder md = new StringBuilder();
        md.append("# NEC Pledge Availability\n\n");
        md.append("This report samples up to `").append(AVAILABILITY_SAMPLE_LIMIT)
                .append("` candidates for each `sgId/sgTypecode` pair to identify where pledge data exists.\n\n");
        md.append("| sgId | sgTypecode | sgName | Date | Candidates | With pledges | Pledges | First candidate | First pledge |\n");
        md.append("| --- | --- | --- | --- | ---: | ---: | ---: | --- | --- |\n");
        for (PledgeAvailability row : rows) {
            md.append("| ").append(escapeMarkdown(row.election.sgId))
                    .append(" | ").append(escapeMarkdown(row.election.sgTypecode))
                    .append(" | ").append(escapeMarkdown(row.election.sgName))
                    .append(" | ").append(escapeMarkdown(row.election.sgVotedate))
                    .append(" | ").append(row.sampledCandidates)
                    .append(" | ").append(row.candidatesWithPledges)
                    .append(" | ").append(row.sampledPledges)
                    .append(" | ").append(escapeMarkdown(firstNonBlank(row.firstCandidateName, row.firstCandidateId)))
                    .append(" | ").append(escapeMarkdown(row.firstPledgeTitle))
                    .append(" |\n");
        }
        Files.writeString(outputDir.resolve("pledge-availability.md"), md.toString(), StandardCharsets.UTF_8);
    }

    private static void printHelp() {
        System.out.println("""
                NEC data probe

                Usage:
                  java tools/nec-probe/NecDataProbe.java --fixture
                  DATA_GO_KR_SERVICE_KEY='...' java tools/nec-probe/NecDataProbe.java --target 2022-local

                Options:
                  --fixture                 Run with embedded sample data.
                  --target VALUE            2022-local, 2025-president, docs-sample, pledge-availability, all, or custom.
                  --sgId VALUE              Required for --target custom.
                  --sgTypecode VALUE        Required for --target custom.
                  --candidate-id VALUE      Optional direct cnddtId/huboid pledge probe.
                  --sdName VALUE            Optional city/province filter.
                  --sggName VALUE           Optional election district filter.
                  --candidate-limit N       Candidate sample size. Default: 30.
                  --output DIR              Output directory. Default: data/probes.
                  --service-key VALUE       Overrides DATA_GO_KR_SERVICE_KEY.
                  --help                    Show this help.
                """);
    }

    private record Config(
            String serviceKey,
            String target,
            String customSgId,
            String customSgTypecode,
            String sdName,
            String sggName,
            String candidateId,
            int candidateLimit,
            Path outputDir,
            boolean fixtureMode,
            boolean showHelp
    ) {
        static Config parse(String[] args) {
            Map<String, String> values = new LinkedHashMap<>();
            boolean fixture = false;
            boolean help = false;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--fixture" -> fixture = true;
                    case "--help", "-h" -> help = true;
                    case "--target", "--sgId", "--sgTypecode", "--sdName", "--sggName",
                            "--candidate-id", "--candidate-limit", "--output", "--service-key" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException(arg + " requires a value");
                        }
                        values.put(arg, args[++i]);
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            String serviceKey = firstNonBlank(values.get("--service-key"), System.getenv("DATA_GO_KR_SERVICE_KEY"));
            String target = firstNonBlank(values.get("--target"), "2022-local");
            String customSgId = firstNonBlank(values.get("--sgId"), "");
            String customSgTypecode = firstNonBlank(values.get("--sgTypecode"), "");
            String sdName = firstNonBlank(values.get("--sdName"), "");
            String sggName = firstNonBlank(values.get("--sggName"), "");
            String candidateId = firstNonBlank(values.get("--candidate-id"), "");
            int candidateLimit = parsePositiveInt(values.get("--candidate-limit"), DEFAULT_CANDIDATE_LIMIT);
            Path outputDir = Path.of(firstNonBlank(values.get("--output"), DEFAULT_OUTPUT_DIR));

            return new Config(serviceKey, target, customSgId, customSgTypecode, sdName, sggName, candidateId,
                    candidateLimit, outputDir, fixture, help);
        }

        private static int parsePositiveInt(String value, int fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException("Value must be positive: " + value);
            }
            return parsed;
        }
    }

    private record TargetSpec(String slug, String sgId, String sgTypecode, String displayName) {
        static List<TargetSpec> resolve(Config config) {
            String target = config.target.toLowerCase(Locale.ROOT);
            return switch (target) {
                case "2022-local" -> List.of(
                        new TargetSpec("2022-local", "20220601", "3", "제8회 전국동시지방선거 시도지사"),
                        new TargetSpec("2022-local", "20220601", "4", "제8회 전국동시지방선거 구시군의 장"),
                        new TargetSpec("2022-local", "20220601", "11", "제8회 전국동시지방선거 교육감")
                );
                case "2025-president" -> List.of(
                        new TargetSpec("2025-president", "20250603", "1", "제21대 대통령선거")
                );
                case "docs-sample" -> List.of(
                        new TargetSpec("docs-sample", "20231011", "4", "API documentation sample")
                );
                case "pledge-availability" -> List.of();
                case "all" -> {
                    List<TargetSpec> all = new ArrayList<>();
                    all.addAll(resolve(new Config(config.serviceKey, "2022-local", "", "", config.sdName, config.sggName, config.candidateId,
                            config.candidateLimit, config.outputDir, false, false)));
                    all.addAll(resolve(new Config(config.serviceKey, "2025-president", "", "", config.sdName, config.sggName, config.candidateId,
                            config.candidateLimit, config.outputDir, false, false)));
                    all.addAll(resolve(new Config(config.serviceKey, "docs-sample", "", "", config.sdName, config.sggName, config.candidateId,
                            config.candidateLimit, config.outputDir, false, false)));
                    yield all;
                }
                case "custom" -> {
                    if (config.customSgId.isBlank() || config.customSgTypecode.isBlank()) {
                        throw new IllegalArgumentException("--target custom requires --sgId and --sgTypecode");
                    }
                    yield List.of(new TargetSpec("custom-" + config.customSgId, config.customSgId,
                            config.customSgTypecode, "Custom election"));
                }
                default -> throw new IllegalArgumentException("Unknown target: " + config.target);
            };
        }
    }

    private static final class NecOpenApiClient {
        private static final String BASE_URL = "http://apis.data.go.kr/9760000";
        private final String serviceKey;
        private final HttpClient client = HttpClient.newHttpClient();

        private NecOpenApiClient(String serviceKey) {
            this.serviceKey = serviceKey;
        }

        List<ElectionCode> fetchElectionCodes() throws IOException, InterruptedException {
            List<ElectionCode> result = new ArrayList<>();
            int page = 1;
            int total = Integer.MAX_VALUE;

            while ((page - 1) * PAGE_SIZE < total) {
                XmlResponse response = get("/CommonCodeService/getCommonSgCodeList", Map.of(
                        "pageNo", String.valueOf(page),
                        "numOfRows", String.valueOf(PAGE_SIZE)
                ));
                total = response.totalCount;
                for (Map<String, String> item : response.items) {
                    result.add(new ElectionCode(
                            item.getOrDefault("sgId", ""),
                            item.getOrDefault("sgTypecode", ""),
                            item.getOrDefault("sgName", ""),
                            item.getOrDefault("sgVotedate", "")
                    ));
                }
                page++;
            }
            return result;
        }

        List<Candidate> fetchCandidates(String sgId, String sgTypecode, String sdName, String sggName, int limit)
                throws IOException, InterruptedException {
            List<Candidate> result = new ArrayList<>();
            int page = 1;
            int total = Integer.MAX_VALUE;

            while ((page - 1) * PAGE_SIZE < total && result.size() < limit) {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("pageNo", String.valueOf(page));
                params.put("numOfRows", String.valueOf(PAGE_SIZE));
                params.put("sgId", sgId);
                params.put("sgTypecode", sgTypecode);
                putIfPresent(params, "sdName", sdName);
                putIfPresent(params, "sggName", sggName);

                XmlResponse response = get("/PofelcddInfoInqireService/getPofelcddRegistSttusInfoInqire", params);
                total = response.totalCount;
                if (response.isNoData()) {
                    System.out.println("No candidate data for sgId=" + sgId + ", sgTypecode=" + sgTypecode
                            + ", sdName=" + sdName + ", sggName=" + sggName);
                    break;
                }
                for (Map<String, String> item : response.items) {
                    if (result.size() >= limit) {
                        break;
                    }
                    result.add(new Candidate(
                            item.getOrDefault("sgId", sgId),
                            item.getOrDefault("sgTypecode", sgTypecode),
                            firstNonBlank(item.get("huboid"), item.get("cnddtid"), item.get("cnddtId")),
                            item.getOrDefault("sdName", ""),
                            item.getOrDefault("sggName", ""),
                            item.getOrDefault("wiwName", ""),
                            item.getOrDefault("jdName", ""),
                            item.getOrDefault("name", ""),
                            item.getOrDefault("status", "")
                    ));
                }
                page++;
            }

            return result;
        }

        List<Pledge> fetchPledges(String sgId, String sgTypecode, String candidateId)
                throws IOException, InterruptedException {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("pageNo", "1");
            params.put("numOfRows", "10");
            params.put("sgId", sgId);
            params.put("sgTypecode", sgTypecode);
            params.put("cnddtId", candidateId);

            XmlResponse response = get("/ElecPrmsInfoInqireService/getCnddtElecPrmsInfoInqire", params);
            if (response.isNoData()) {
                return List.of();
            }
            List<Pledge> pledges = new ArrayList<>();
            for (Map<String, String> item : response.items) {
                int pledgeCount = parseInt(firstNonBlank(item.get("prmsCnt"), "10"), 10);
                for (int order = 1; order <= Math.max(10, pledgeCount); order++) {
                    String title = item.getOrDefault("prmsTitle" + order, "").trim();
                    if (title.isBlank()) {
                        continue;
                    }
                    String id = candidateId + "-" + order;
                    pledges.add(new Pledge(
                            id,
                            item.getOrDefault("sgId", sgId),
                            item.getOrDefault("sgTypecode", sgTypecode),
                            firstNonBlank(item.get("cnddtId"), item.get("cnddtid"), candidateId),
                            item.getOrDefault("krName", ""),
                            item.getOrDefault("partyName", ""),
                            item.getOrDefault("sidoName", ""),
                            item.getOrDefault("sggName", ""),
                            item.getOrDefault("prmsRealmName" + order, ""),
                            title,
                            firstNonBlank(item.get("prmmCont" + order), item.get("prmsCont" + order)),
                            order
                    ));
                }
            }
            return pledges;
        }

        private XmlResponse get(String path, Map<String, String> params) throws IOException, InterruptedException {
            String url = buildUrl(path, params);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/xml")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode() + " from " + path);
            }
            return XmlResponse.parse(response.body(), path);
        }

        private String buildUrl(String path, Map<String, String> params) {
            StringBuilder url = new StringBuilder(BASE_URL).append(path)
                    .append("?ServiceKey=").append(encodeServiceKey(serviceKey));
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                url.append('&')
                        .append(encode(entry.getKey()))
                        .append('=')
                        .append(encode(entry.getValue()));
            }
            return url.toString();
        }

        private static String encodeServiceKey(String value) {
            if (value.contains("%2F") || value.contains("%2B") || value.contains("%3D")) {
                return value;
            }
            return encode(value);
        }

        private static String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
        }
    }

    private record XmlResponse(String resultCode, String resultMessage, int totalCount, List<Map<String, String>> items) {
        boolean isNoData() {
            String normalizedCode = resultCode == null ? "" : resultCode.trim().toUpperCase(Locale.ROOT);
            String normalizedMessage = resultMessage == null ? "" : resultMessage.trim();
            return normalizedCode.equals("INFO-03")
                    || normalizedCode.equals("03")
                    || normalizedMessage.contains("데이터 정보가 없습니다");
        }

        static XmlResponse parse(String xml, String path) throws IOException {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(false);
                factory.setExpandEntityReferences(false);
                try {
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                } catch (Exception ignored) {
                    // Some JDK XML parsers do not support every hardening feature.
                }

                Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
                String resultCode = firstNonBlank(text(document, "resultCode"), text(document, "returnReasonCode"));
                String resultMessage = firstNonBlank(text(document, "resultMsg"), text(document, "returnAuthMsg"));
                int totalCount = parseInt(text(document, "totalCount"), 0);
                List<Map<String, String>> items = new ArrayList<>();
                NodeList itemNodes = document.getElementsByTagName("item");
                for (int i = 0; i < itemNodes.getLength(); i++) {
                    Node node = itemNodes.item(i);
                    if (node instanceof Element element) {
                        items.add(toMap(element));
                    }
                }
                if (totalCount == 0 && !items.isEmpty()) {
                    totalCount = items.size();
                }
                XmlResponse response = new XmlResponse(resultCode, resultMessage, totalCount, items);
                if (!resultCode.isBlank() && !isSuccess(resultCode) && !response.isNoData()) {
                    throw new IOException("API error from " + path + ": " + resultCode + " " + resultMessage);
                }
                return response;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Failed to parse XML from " + path + ": " + e.getMessage(), e);
            }
        }

        private static boolean isSuccess(String resultCode) {
            String normalized = resultCode.trim().toUpperCase(Locale.ROOT);
            return normalized.equals("00") || normalized.equals("INFO-00") || normalized.equals("0");
        }

        private static Map<String, String> toMap(Element element) {
            Map<String, String> values = new LinkedHashMap<>();
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element childElement) {
                    values.put(childElement.getTagName(), childElement.getTextContent().trim());
                }
            }
            return values;
        }

        private static String text(Document document, String tagName) {
            NodeList nodes = document.getElementsByTagName(tagName);
            if (nodes.getLength() == 0) {
                return "";
            }
            return nodes.item(0).getTextContent().trim();
        }
    }

    private record ElectionCode(String sgId, String sgTypecode, String sgName, String sgVotedate) {
    }

    private record Candidate(
            String sgId,
            String sgTypecode,
            String candidateId,
            String sdName,
            String sggName,
            String wiwName,
            String partyName,
            String name,
            String status
    ) {
    }

    private record Pledge(
            String id,
            String sgId,
            String sgTypecode,
            String candidateId,
            String candidateName,
            String partyName,
            String sdName,
            String sggName,
            String category,
            String title,
            String content,
            int order
    ) {
        Pledge withCandidate(Candidate candidate) {
            return new Pledge(
                    id,
                    sgId,
                    sgTypecode,
                    candidateId,
                    firstNonBlank(candidateName, candidate.name),
                    firstNonBlank(partyName, candidate.partyName),
                    firstNonBlank(sdName, candidate.sdName),
                    firstNonBlank(sggName, candidate.sggName),
                    category,
                    title,
                    content,
                    order
            );
        }
    }

    private record ProbeRun(ElectionCode election, List<Candidate> candidates, List<Pledge> pledges) {
    }

    private record SampleResponse(String pledgeId, String value, int score) {
    }

    private record CandidateScore(
            String candidateId,
            String candidateName,
            String partyName,
            String sdName,
            String sggName,
            int evaluatedPledgeCount,
            int totalScore,
            double averageScore,
            double normalizedScore
    ) {
    }

    private record PledgeAvailability(
            ElectionCode election,
            int sampledCandidates,
            int candidatesWithPledges,
            int sampledPledges,
            String firstCandidateId,
            String firstCandidateName,
            String firstPledgeTitle
    ) {
    }

    private static final class ProbeReport {
        private final String generatedAt;
        private final String mode;
        private final Config config;
        private final ProbeRun run;
        private final List<SampleResponse> responses;
        private final List<CandidateScore> ranking;
        private final List<Candidate> candidatesWithoutPledges;

        private ProbeReport(
                String generatedAt,
                String mode,
                Config config,
                ProbeRun run,
                List<SampleResponse> responses,
                List<CandidateScore> ranking,
                List<Candidate> candidatesWithoutPledges
        ) {
            this.generatedAt = generatedAt;
            this.mode = mode;
            this.config = config;
            this.run = run;
            this.responses = responses;
            this.ranking = ranking;
            this.candidatesWithoutPledges = candidatesWithoutPledges;
        }

        static ProbeReport from(ProbeRun run, String mode, Config config) {
            List<SampleResponse> responses = buildSampleResponses(run.pledges);
            List<CandidateScore> ranking = scoreCandidates(run, responses);
            List<Candidate> candidatesWithoutPledges = findCandidatesWithoutPledges(run);
            return new ProbeReport(Instant.now().toString(), mode, config, run, responses, ranking, candidatesWithoutPledges);
        }

        String toMarkdown() {
            StringBuilder md = new StringBuilder();
            md.append("# NEC Data Validation Report\n\n");
            md.append("- Generated at: `").append(generatedAt).append("`\n");
            md.append("- Mode: `").append(mode).append("`\n");
            md.append("- Election: `").append(run.election.sgName).append("` (`")
                    .append(run.election.sgId).append("`, type `").append(run.election.sgTypecode).append("`)\n");
            if (!config.sdName.isBlank() || !config.sggName.isBlank()) {
                md.append("- Filter: `sdName=").append(config.sdName).append("`, `sggName=")
                        .append(config.sggName).append("`\n");
            }
            md.append("- Candidates fetched: `").append(run.candidates.size()).append("`\n");
            md.append("- Pledges fetched: `").append(run.pledges.size()).append("`\n");
            md.append("- Candidates without pledges: `").append(candidatesWithoutPledges.size()).append("`\n\n");

            md.append("## Candidate Ranking\n\n");
            if (ranking.isEmpty()) {
                md.append("No ranking was calculated because no pledges were available. ")
                        .append("Check `../common-codes/common-codes.md` and retry with an actual `sgId/sgTypecode` pair if this target is empty.\n\n");
            } else {
                md.append("| Rank | Candidate | Region | Evaluated | Average | Match |\n");
                md.append("| ---: | --- | --- | ---: | ---: | ---: |\n");
                int rank = 1;
                for (CandidateScore score : ranking) {
                    md.append("| ").append(rank++)
                            .append(" | ").append(escapeMarkdown(score.candidateName))
                            .append(" | ").append(escapeMarkdown(firstNonBlank(score.sggName, score.sdName)))
                            .append(" | ").append(score.evaluatedPledgeCount)
                            .append(" | ").append(format(score.averageScore))
                            .append(" | ").append(format(score.normalizedScore)).append("% |\n");
                }
                md.append('\n');
            }

            md.append("## Candidates Without Pledges\n\n");
            if (candidatesWithoutPledges.isEmpty()) {
                md.append("None in this sample.\n\n");
            } else {
                for (Candidate candidate : candidatesWithoutPledges) {
                    md.append("- `").append(candidate.candidateId).append("` ")
                            .append(candidate.name).append(" / ").append(candidate.partyName)
                            .append(" / ").append(firstNonBlank(candidate.sggName, candidate.sdName))
                            .append('\n');
                }
                md.append('\n');
            }

            md.append("## User-Only Evidence Sample\n\n");
            md.append("This section demonstrates the result evidence that should be shown only to the current user.\n\n");
            int evidenceLimit = Math.min(run.pledges.size(), 20);
            for (int i = 0; i < evidenceLimit; i++) {
                Pledge pledge = run.pledges.get(i);
                SampleResponse response = responses.get(i);
                md.append("- [").append(response.value).append(" / ").append(response.score).append("점] ")
                        .append(pledge.title).append(" - ")
                        .append(pledge.candidateName).append(" (")
                        .append(firstNonBlank(pledge.sggName, pledge.sdName)).append(")\n");
            }
            return md.toString();
        }

        String toJson() {
            JsonWriter json = new JsonWriter();
            json.beginObject();
            json.name("generatedAt").value(generatedAt);
            json.name("mode").value(mode);
            json.name("election").beginObject()
                    .name("sgId").value(run.election.sgId)
                    .name("sgTypecode").value(run.election.sgTypecode)
                    .name("sgName").value(run.election.sgName)
                    .name("sgVotedate").value(run.election.sgVotedate)
                    .endObject();
            json.name("filters").beginObject()
                    .name("sdName").value(config.sdName)
                    .name("sggName").value(config.sggName)
                    .name("candidateLimit").value(config.candidateLimit)
                    .endObject();
            json.name("counts").beginObject()
                    .name("candidates").value(run.candidates.size())
                    .name("pledges").value(run.pledges.size())
                    .name("candidatesWithoutPledges").value(candidatesWithoutPledges.size())
                    .endObject();

            json.name("ranking").beginArray();
            for (CandidateScore score : ranking) {
                json.beginObject()
                        .name("candidateId").value(score.candidateId)
                        .name("candidateName").value(score.candidateName)
                        .name("partyName").value(score.partyName)
                        .name("sdName").value(score.sdName)
                        .name("sggName").value(score.sggName)
                        .name("evaluatedPledgeCount").value(score.evaluatedPledgeCount)
                        .name("totalScore").value(score.totalScore)
                        .name("averageScore").value(round(score.averageScore))
                        .name("normalizedScore").value(round(score.normalizedScore))
                        .endObject();
            }
            json.endArray();

            json.name("candidatesWithoutPledges").beginArray();
            for (Candidate candidate : candidatesWithoutPledges) {
                writeCandidate(json, candidate);
            }
            json.endArray();

            json.name("pledges").beginArray();
            for (Pledge pledge : run.pledges) {
                json.beginObject()
                        .name("id").value(pledge.id)
                        .name("candidateId").value(pledge.candidateId)
                        .name("candidateName").value(pledge.candidateName)
                        .name("partyName").value(pledge.partyName)
                        .name("sdName").value(pledge.sdName)
                        .name("sggName").value(pledge.sggName)
                        .name("category").value(pledge.category)
                        .name("title").value(pledge.title)
                        .name("content").value(pledge.content)
                        .name("order").value(pledge.order)
                        .endObject();
            }
            json.endArray();

            json.name("sampleResponses").beginArray();
            for (SampleResponse response : responses) {
                json.beginObject()
                        .name("pledgeId").value(response.pledgeId)
                        .name("value").value(response.value)
                        .name("score").value(response.score)
                        .endObject();
            }
            json.endArray();

            json.endObject();
            return json.toString();
        }

        private static void writeCandidate(JsonWriter json, Candidate candidate) {
            json.beginObject()
                    .name("candidateId").value(candidate.candidateId)
                    .name("name").value(candidate.name)
                    .name("partyName").value(candidate.partyName)
                    .name("sdName").value(candidate.sdName)
                    .name("sggName").value(candidate.sggName)
                    .name("wiwName").value(candidate.wiwName)
                    .name("status").value(candidate.status)
                    .endObject();
        }

        private static List<SampleResponse> buildSampleResponses(List<Pledge> pledges) {
            List<SampleResponse> responses = new ArrayList<>();
            for (int i = 0; i < pledges.size(); i++) {
                Pledge pledge = pledges.get(i);
                int mod = i % 3;
                if (mod == 0) {
                    responses.add(new SampleResponse(pledge.id, "긍정적이다", 3));
                } else if (mod == 1) {
                    responses.add(new SampleResponse(pledge.id, "모르겠다", 2));
                } else {
                    responses.add(new SampleResponse(pledge.id, "부정적이다", 1));
                }
            }
            return responses;
        }

        private static List<CandidateScore> scoreCandidates(ProbeRun run, List<SampleResponse> responses) {
            Map<String, CandidateAccumulator> accumulators = new LinkedHashMap<>();
            for (Candidate candidate : run.candidates) {
                accumulators.put(candidate.candidateId, new CandidateAccumulator(candidate));
            }

            Map<String, Integer> scoreByPledgeId = new LinkedHashMap<>();
            for (SampleResponse response : responses) {
                scoreByPledgeId.put(response.pledgeId, response.score);
            }

            for (Pledge pledge : run.pledges) {
                CandidateAccumulator accumulator = accumulators.computeIfAbsent(
                        pledge.candidateId,
                        ignored -> new CandidateAccumulator(new Candidate(
                                pledge.sgId,
                                pledge.sgTypecode,
                                pledge.candidateId,
                                pledge.sdName,
                                pledge.sggName,
                                "",
                                pledge.partyName,
                                pledge.candidateName,
                                ""
                        ))
                );
                Integer score = scoreByPledgeId.get(pledge.id);
                if (score != null) {
                    accumulator.totalScore += score;
                    accumulator.evaluatedPledgeCount++;
                }
            }

            return accumulators.values().stream()
                    .filter(acc -> acc.evaluatedPledgeCount > 0)
                    .map(CandidateAccumulator::toScore)
                    .sorted(Comparator
                            .comparingDouble(CandidateScore::normalizedScore).reversed()
                            .thenComparing(Comparator.comparingInt(CandidateScore::evaluatedPledgeCount).reversed())
                            .thenComparing(CandidateScore::candidateName, Comparator.nullsLast(String::compareTo)))
                    .toList();
        }

        private static List<Candidate> findCandidatesWithoutPledges(ProbeRun run) {
            Map<String, Boolean> hasPledge = new LinkedHashMap<>();
            for (Pledge pledge : run.pledges) {
                hasPledge.put(pledge.candidateId, true);
            }
            return run.candidates.stream()
                    .filter(candidate -> !hasPledge.containsKey(candidate.candidateId))
                    .toList();
        }
    }

    private static final class CandidateAccumulator {
        private final Candidate candidate;
        private int totalScore;
        private int evaluatedPledgeCount;

        private CandidateAccumulator(Candidate candidate) {
            this.candidate = candidate;
        }

        private CandidateScore toScore() {
            double average = evaluatedPledgeCount == 0 ? 0 : (double) totalScore / evaluatedPledgeCount;
            double normalized = ((average - 1.0) / 2.0) * 100.0;
            return new CandidateScore(
                    candidate.candidateId,
                    candidate.name,
                    candidate.partyName,
                    candidate.sdName,
                    candidate.sggName,
                    evaluatedPledgeCount,
                    totalScore,
                    average,
                    clamp(normalized, 0, 100)
            );
        }
    }

    private static final class JsonWriter {
        private final StringBuilder out = new StringBuilder();
        private final List<Boolean> firstStack = new ArrayList<>();
        private boolean expectingValue = false;

        JsonWriter beginObject() {
            beforeValue();
            out.append('{');
            firstStack.add(true);
            return this;
        }

        JsonWriter endObject() {
            out.append('}');
            firstStack.remove(firstStack.size() - 1);
            expectingValue = false;
            return this;
        }

        JsonWriter beginArray() {
            beforeValue();
            out.append('[');
            firstStack.add(true);
            return this;
        }

        JsonWriter endArray() {
            out.append(']');
            firstStack.remove(firstStack.size() - 1);
            expectingValue = false;
            return this;
        }

        JsonWriter name(String name) {
            beforeName();
            string(name);
            out.append(':');
            expectingValue = true;
            return this;
        }

        JsonWriter value(String value) {
            beforeValue();
            if (value == null) {
                out.append("null");
            } else {
                string(value);
            }
            return this;
        }

        JsonWriter value(int value) {
            beforeValue();
            out.append(value);
            return this;
        }

        JsonWriter value(double value) {
            beforeValue();
            out.append(value);
            return this;
        }

        private void beforeName() {
            if (firstStack.isEmpty()) {
                return;
            }
            int last = firstStack.size() - 1;
            if (firstStack.get(last)) {
                firstStack.set(last, false);
            } else {
                out.append(',');
            }
        }

        private void beforeValue() {
            if (expectingValue) {
                expectingValue = false;
                return;
            }
            if (!firstStack.isEmpty()) {
                int last = firstStack.size() - 1;
                if (firstStack.get(last)) {
                    firstStack.set(last, false);
                } else {
                    out.append(',');
                }
            }
        }

        private void string(String value) {
            out.append('"');
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                switch (ch) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (ch < 0x20) {
                            out.append(String.format("\\u%04x", (int) ch));
                        } else {
                            out.append(ch);
                        }
                    }
                }
            }
            out.append('"');
        }

        @Override
        public String toString() {
            return out.toString();
        }
    }

    private static void putIfPresent(Map<String, String> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String escapeMarkdown(String value) {
        return Objects.toString(value, "").replace("|", "\\|");
    }
}
