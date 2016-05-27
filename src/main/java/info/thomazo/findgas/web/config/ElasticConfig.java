package info.thomazo.findgas.web.config;

import lombok.Getter;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Configuration
public class ElasticConfig {

	@Value("${app.index.name:stations}")
	@Getter
	private String indexName;

	@Value("${app.index.type.station:station}")
	@Getter
	private String stationType;

	@Value("${app.index.type.comment:comment}")
	@Getter
	private String commentType;

	@Value("${app.index.type.patch:patch}")
	@Getter
	private String patchType;

	@Value("${es.hosts:localhost}")
	private String[] esHosts;


	@Bean(destroyMethod = "close")
	public Client esClient() throws UnknownHostException {
		TransportClient client = TransportClient.builder().build();

		for (String esHost : esHosts) {
			client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(esHost), 9300));
		}


		return client;
	}

}
