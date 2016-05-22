package info.thomazo.findgas.web.api;

import com.github.davidmoten.geo.GeoHash;
import com.github.davidmoten.geo.LatLong;
import info.thomazo.findgas.web.config.ElasticConfig;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.geogrid.GeoHashGrid;
import org.geojson.Feature;
import org.geojson.GeoJsonObject;
import org.geojson.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/api/stations")
public class StationsCtrl {

	@Autowired
	private Client esClient;

	@Autowired
	private ElasticConfig esConfig;

	@RequestMapping(method = GET)
	public List<GeoJsonObject> list(@RequestParam double n, @RequestParam double s, @RequestParam double w, @RequestParam double e, @RequestParam int z) {

		SearchResponse res = esClient.prepareSearch(esConfig.getIndexName()).setTypes(esConfig.getStationType())
				.setQuery(QueryBuilders.boolQuery().filter(QueryBuilders.geoBoundingBoxQuery("location").topLeft(n, w).bottomRight(s, e)))
				.setSize(100)
				.execute()
				.actionGet();

		if (res.getHits().getTotalHits() > 100) {
			return listAggregate(n, s, w, e, z);
		}

		List<GeoJsonObject> resStations = new ArrayList<>((int) res.getHits().totalHits());

		res.getHits().forEach(h -> {
			Feature f = new Feature();
			f.setProperty("id", h.getId());
			setProperty(h, f, "address");
			setProperty(h, f, "cp");
			setProperty(h, f, "city");
			f.setGeometry(getPoint(h.getSource().get("location").toString()));

			resStations.add(f);
		});

		return resStations;
	}


	private List<GeoJsonObject> listAggregate(double n, double s, double w, double e, int z) {
		int zoom = z - 4;
		if (zoom < 5) zoom = 5;

		SearchResponse res = esClient.prepareSearch(esConfig.getIndexName()).setTypes(esConfig.getStationType())
				.setQuery(QueryBuilders.boolQuery().filter(QueryBuilders.geoBoundingBoxQuery("location").topLeft(n, w).bottomRight(s, e)))
				.addAggregation(AggregationBuilders.geohashGrid("stations").field("location").precision(zoom))
				.setSize(100)
				.execute()
				.actionGet();


		GeoHashGrid stations = res.getAggregations().get("stations");
		List<GeoHashGrid.Bucket> buckets = stations.getBuckets();
		List<GeoJsonObject> resStations = new ArrayList<>(buckets.size());

		for (GeoHashGrid.Bucket bucket : buckets) {
			Feature f = new Feature();

			LatLong latLong = GeoHash.decodeHash(bucket.getKeyAsString());
			f.setGeometry(new Point(latLong.getLon(), latLong.getLat()));

			f.setProperty("nb", bucket.getDocCount());
			f.setProperty("radius", getRadiusFromGeohash(bucket.getKeyAsString()));

			resStations.add(f);
		}

		return resStations;
	}

	private double getRadiusFromGeohash(String geohash) {
		switch (geohash.length()) {
			case 1: return 2500 / 2;
			case 2: return 630 / 2;
			case 3: return 78 / 2;
			case 4: return 20 / 2;
			case 5: return 2.4 / 2;
			case 6: return 0.61 / 2;
			case 7: return 0.076 / 2;
			case 8: return 0.019 / 2;
		}
		return 0;
	}

	private GeoJsonObject getPoint(String location) {
		int idx = location.indexOf(',');
		if (idx == -1) return null;

		double lat = Double.parseDouble(location.substring(0, idx));
		double lng = Double.parseDouble(location.substring(idx + 2));

		return new Point(lng, lat);
	}

	private void setProperty(SearchHit hit, Feature feature, String fieldName) {
		Object value = hit.getSource().get(fieldName);
		if (value != null) {
			feature.setProperty(fieldName, value);
		}
	}
}
