package com.pledgedecoding.importer.nec;

import static com.pledgedecoding.importer.nec.NecXmlResponse.firstNonBlank;
import static com.pledgedecoding.importer.nec.NecXmlResponse.parseInt;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NecApiClient {
	private static final int PAGE_SIZE = 100;
	private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

	private final String baseUrl;
	private final String serviceKey;
	private final HttpClient client;

	public NecApiClient(
			@Value("${nec.api.base-url}") String baseUrl,
			@Value("${nec.api.service-key:}") String serviceKey
	) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.serviceKey = serviceKey;
		this.client = HttpClient.newHttpClient();
	}

	public List<NecElectionCode> fetchElectionCodes() {
		requireServiceKey();
		List<NecElectionCode> result = new ArrayList<>();
		int page = 1;
		int total = Integer.MAX_VALUE;

		while ((page - 1) * PAGE_SIZE < total) {
			NecXmlResponse response = get("/CommonCodeService/getCommonSgCodeList", Map.of(
					"pageNo", String.valueOf(page),
					"numOfRows", String.valueOf(PAGE_SIZE)
			));
			total = response.totalCount();
			for (Map<String, String> item : response.items()) {
				result.add(new NecElectionCode(
						item.getOrDefault("sgId", ""),
						item.getOrDefault("sgTypecode", ""),
						item.getOrDefault("sgName", ""),
						parseDate(item.get("sgVotedate"))
				));
			}
			page++;
		}
		return result;
	}

	public List<NecCandidate> fetchCandidates(String sgId, String sgTypecode, String sdName, String sggName, int maxCandidates) {
		requireServiceKey();
		List<NecCandidate> result = new ArrayList<>();
		int page = 1;
		int total = Integer.MAX_VALUE;

		while ((page - 1) * PAGE_SIZE < total && (maxCandidates <= 0 || result.size() < maxCandidates)) {
			Map<String, String> params = new LinkedHashMap<>();
			params.put("pageNo", String.valueOf(page));
			params.put("numOfRows", String.valueOf(PAGE_SIZE));
			params.put("sgId", sgId);
			params.put("sgTypecode", sgTypecode);
			putIfPresent(params, "sdName", sdName);
			putIfPresent(params, "sggName", sggName);

			NecXmlResponse response = get("/PofelcddInfoInqireService/getPofelcddRegistSttusInfoInqire", params);
			total = response.totalCount();
			if (response.isNoData()) {
				break;
			}

			for (Map<String, String> item : response.items()) {
				if (maxCandidates > 0 && result.size() >= maxCandidates) {
					break;
				}
				String candidateId = firstNonBlank(item.get("huboid"), item.get("cnddtId"), item.get("cnddtid"));
				if (!StringUtils.hasText(candidateId)) {
					continue;
				}
				result.add(new NecCandidate(
						item.getOrDefault("sgId", sgId),
						item.getOrDefault("sgTypecode", sgTypecode),
						candidateId,
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

	public List<NecPledge> fetchPledges(String sgId, String sgTypecode, String candidateId) {
		requireServiceKey();
		Map<String, String> params = new LinkedHashMap<>();
		params.put("pageNo", "1");
		params.put("numOfRows", "100");
		params.put("sgId", sgId);
		params.put("sgTypecode", sgTypecode);
		params.put("cnddtId", candidateId);

		NecXmlResponse response = get("/ElecPrmsInfoInqireService/getCnddtElecPrmsInfoInqire", params);
		if (response.isNoData()) {
			return List.of();
		}

		List<NecPledge> pledges = new ArrayList<>();
		for (Map<String, String> item : response.items()) {
			int pledgeCount = parseInt(firstNonBlank(item.get("prmsCnt"), "10"), 10);
			for (int index = 1; index <= Math.max(10, pledgeCount); index++) {
				String title = item.getOrDefault("prmsTitle" + index, "").trim();
				if (!StringUtils.hasText(title)) {
					continue;
				}
				int order = parseInt(item.get("prmsOrd" + index), index);
				pledges.add(new NecPledge(
						item.getOrDefault("sgId", sgId),
						item.getOrDefault("sgTypecode", sgTypecode),
						firstNonBlank(item.get("cnddtId"), item.get("cnddtid"), candidateId),
						item.getOrDefault("krName", ""),
						item.getOrDefault("partyName", ""),
						item.getOrDefault("sidoName", ""),
						item.getOrDefault("sggName", ""),
						order,
						item.getOrDefault("prmsRealmName" + index, ""),
						title,
						firstNonBlank(item.get("prmmCont" + index), item.get("prmsCont" + index))
				));
			}
		}
		return pledges;
	}

	private NecXmlResponse get(String path, Map<String, String> params) {
		String url = buildUrl(path, params);
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.header("Accept", "application/xml")
				.GET()
				.build();
		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new NecApiException("NEC API HTTP " + response.statusCode() + " from " + path);
			}
			return NecXmlResponse.parse(response.body(), path);
		} catch (IOException e) {
			throw new NecApiException("NEC API request failed from " + path, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new NecApiException("NEC API request interrupted from " + path, e);
		}
	}

	private String buildUrl(String path, Map<String, String> params) {
		StringBuilder url = new StringBuilder(baseUrl)
				.append(path)
				.append("?ServiceKey=")
				.append(encodeServiceKey(serviceKey));
		for (Map.Entry<String, String> entry : params.entrySet()) {
			if (!StringUtils.hasText(entry.getValue())) {
				continue;
			}
			url.append('&')
					.append(encode(entry.getKey()))
					.append('=')
					.append(encode(entry.getValue()));
		}
		return url.toString();
	}

	private void requireServiceKey() {
		if (!StringUtils.hasText(serviceKey)) {
			throw new NecApiException("DATA_GO_KR_SERVICE_KEY is not configured");
		}
	}

	private static void putIfPresent(Map<String, String> params, String key, String value) {
		if (StringUtils.hasText(value)) {
			params.put(key, value);
		}
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

	private static LocalDate parseDate(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return LocalDate.parse(value, BASIC_DATE);
		} catch (DateTimeParseException ignored) {
			return null;
		}
	}
}
