package com.pledgedecoding.importer.nec;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

record NecXmlResponse(String resultCode, String resultMessage, int totalCount, List<Map<String, String>> items) {
	boolean isSuccess() {
		String normalized = resultCode == null ? "" : resultCode.trim().toUpperCase(Locale.ROOT);
		return normalized.isBlank() || normalized.equals("00") || normalized.equals("INFO-00") || normalized.equals("0");
	}

	boolean isNoData() {
		String normalizedCode = resultCode == null ? "" : resultCode.trim().toUpperCase(Locale.ROOT);
		String normalizedMessage = resultMessage == null ? "" : resultMessage.trim();
		return normalizedCode.equals("INFO-03")
				|| normalizedCode.equals("03")
				|| normalizedMessage.contains("데이터 정보가 없습니다");
	}

	static NecXmlResponse parse(String xml, String path) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(false);
			factory.setExpandEntityReferences(false);
			try {
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
				factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			} catch (Exception ignored) {
				// Parser support differs between JDK vendors; keep the safe features when available.
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

			NecXmlResponse response = new NecXmlResponse(resultCode, resultMessage, totalCount, items);
			if (!response.isSuccess() && !response.isNoData()) {
				throw new NecApiException("NEC API error from " + path + ": " + resultCode + " " + resultMessage);
			}
			return response;
		} catch (NecApiException e) {
			throw e;
		} catch (Exception e) {
			throw new NecApiException("Failed to parse NEC XML from " + path, e);
		}
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

	static int parseInt(String value, int fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "";
	}
}
