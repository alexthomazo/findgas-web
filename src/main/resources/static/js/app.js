var mymap = L.map('map').setView([47.9099, -2.5653], 9);

L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
	maxZoom: 19,
	attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>'
}).addTo(mymap);


function pointToLayer(feature, latlng) {
	var geojsonMarkerOptions = {
		fillColor: "#ff7800",
		color: "#000",
		weight: 1,
		opacity: 1,
		fillOpacity: 0.8
	};

	if (feature.properties.radius) {
		var radius = feature.properties.radius * 1000;
		if (radius < 200) radius = 200;
		return L.circle(latlng, radius, geojsonMarkerOptions);

	} else {
		geojsonMarkerOptions.radius = 9;
		return L.circleMarker(latlng, geojsonMarkerOptions);
	}
}

function onFeature(f, layer) {
	var prop = f.properties;
	var nb = prop.nb;
	if (nb) {
		layer.bindPopup(nb + " station" + (nb > 1 ? "s" : "") + " dans cette zone.<br>Zoomez pour voir le détail");
	} else {
		layer.bindPopup(prop.address + "<br>" + prop.cp + " " + prop.city);
	}
}

var geojson = L.geoJson([], {
	pointToLayer: pointToLayer,
	onEachFeature: onFeature
}).addTo(mymap);

var curQuery;
function updateMap() {
	var bounds = mymap.getBounds();

	if (curQuery && curQuery.abort) curQuery.abort();

	curQuery = atomic.get('/api/stations?n=' + bounds.getNorth() + "&s=" + bounds.getSouth()
			+ "&w=" + bounds.getWest() + "&e=" + bounds.getEast() + "&z=" + mymap.getZoom())
		.success(function(data) {
			geojson.clearLayers();
			geojson.addData(data);
		})
		.error(function (data, xhr) {
			console.log(xhr.status);
		})
        .always(function() {
            curQuery = undefined;
        });
}

mymap.on('zoomstart', function() { geojson.clearLayers(); });
mymap.on('dragend', updateMap);
mymap.on('zoomend', updateMap);
mymap.on('locationfound', updateMap);
mymap.on('locationerror', updateMap);
updateMap();

mymap.locate({setView: true, maxZoom: 16});