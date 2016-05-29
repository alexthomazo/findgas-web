package info.thomazo.findgas.web.api;

import info.thomazo.findgas.web.config.ElasticConfig;
import info.thomazo.findgas.web.config.IpMDCFilter;
import info.thomazo.findgas.web.dto.Patch;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping("/api/patch")
public class PatchCtrl {
	private static final Logger logger = LoggerFactory.getLogger(PatchCtrl.class);
	private static final PolicyFactory stripHtml = new HtmlPolicyBuilder().toFactory();

	@Autowired
	private Client esClient;

	@Autowired
	private ElasticConfig esConfig;

	@Autowired
	private IpMDCFilter ipMDCFilter;

	@RequestMapping(method = POST)
	public String post(@RequestBody Patch patch, ServletRequest request) throws IOException {
		sanitzePatch(patch);
		if (patch.getComment() != null && patch.getComment().length() > 130) {
			patch.setComment(patch.getComment().substring(0, 130));
		}
		String location = normalizeLocation(patch.getLocation());
		if (location == null || location.isEmpty()) return "La localisation est invalide";

		IndexResponse idxRes = esClient.prepareIndex(esConfig.getIndexName(), esConfig.getPatchType())
			.setSource(jsonBuilder().startObject()
					.field("station", patch.getStation())
					.field("stationName", patch.getStationName())
					.field("date", new Date())
					.field("address", patch.getAddress())
					.field("city", patch.getCity())
					.field("cp", patch.getCp())
					.field("location", location)
					.field("comment", patch.getComment())
					.field("name", patch.getName())
					.field("ip", ipMDCFilter.getIpV4(request))
					.endObject())
			.get();

		logger.info("[ADDED_PATCH] [id:{}] [stationId:{}] [stName:{}] [address:{}] [cp:{}] [city:{}] [location:{}] [comment:{}] [name:{}]",
				idxRes.getId(), patch.getStation(), patch.getStationName(), patch.getAddress(), patch.getCp(), patch.getCity(), patch.getComment());

		return "ok";
	}


	private String normalizeLocation(String location) {
		if (location == null) return null;
		location = StringUtils.replaceChars(location, " ", "");
		int pos = location.indexOf(',');

		if (pos == -1) {
			return null;
		}

		try {
			BigDecimal lat = new BigDecimal(location.substring(0, pos));
			if (lat.compareTo(new BigDecimal(90)) == 1 || lat.compareTo(new BigDecimal(-90)) == -1) {
				return null;
			}

			BigDecimal lng = new BigDecimal(location.substring(pos+1));
			if (lng.compareTo(new BigDecimal(180)) == 1 || lng.compareTo(new BigDecimal(-180)) == -1) {
				return null;
			}

			return lat.toString() + ", " + lng.toString();
		} catch (Exception e) {
			return null;
		}
	}

	private void sanitzePatch(Patch p) {
		p.setStation(sanitize(p.getStation()));
		p.setStationName(sanitize(p.getStationName()));
		p.setLocation(sanitize(p.getLocation()));
		p.setAddress(sanitize(p.getAddress()));
		p.setCp(sanitize(p.getCp()));
		p.setCity(sanitize(p.getCity()));
		p.setComment(sanitize(p.getComment()));
		p.setName(sanitize(p.getName()));
	}

	private String sanitize(String input) {
		if (input == null) return null;
		String res = stripHtml.sanitize(input);
		if (res.isEmpty()) return null;
		return res;
	}

}
