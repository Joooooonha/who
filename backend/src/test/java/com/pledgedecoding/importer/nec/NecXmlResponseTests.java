package com.pledgedecoding.importer.nec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NecXmlResponseTests {
	@Test
	void parsesPledgeContentFieldFromNecXml() {
		String xml = """
				<response>
				  <header>
				    <resultCode>INFO-00</resultCode>
				    <resultMsg>NORMAL SERVICE</resultMsg>
				  </header>
				  <body>
				    <items>
				      <item>
				        <sgId>20250603</sgId>
				        <sgTypecode>1</sgTypecode>
				        <cnddtId>100153692</cnddtId>
				        <prmsCnt>1</prmsCnt>
				        <prmsOrd1>1</prmsOrd1>
				        <prmsRealmName1>경제·산업</prmsRealmName1>
				        <prmsTitle1>세계를 선도하는 경제 강국을 만들겠습니다.</prmsTitle1>
				        <prmmCont1>공식 공약 본문</prmmCont1>
				      </item>
				    </items>
				    <totalCount>1</totalCount>
				  </body>
				</response>
				""";

		NecXmlResponse response = NecXmlResponse.parse(xml, "/sample");

		assertThat(response.isSuccess()).isTrue();
		assertThat(response.totalCount()).isEqualTo(1);
		assertThat(response.items()).hasSize(1);
		assertThat(response.items().get(0)).containsEntry("prmmCont1", "공식 공약 본문");
	}

	@Test
	void treatsInfo03AsNoData() {
		String xml = """
				<response>
				  <header>
				    <resultCode>INFO-03</resultCode>
				    <resultMsg>데이터 정보가 없습니다. 입력 파라미터값을 확인해주시기 바랍니다.</resultMsg>
				  </header>
				  <body>
				    <items/>
				    <totalCount>0</totalCount>
				  </body>
				</response>
				""";

		NecXmlResponse response = NecXmlResponse.parse(xml, "/sample");

		assertThat(response.isNoData()).isTrue();
		assertThat(response.items()).isEmpty();
	}
}
