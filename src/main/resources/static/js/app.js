var mymap = L.map('map').setView([47.837, -1.093], 8);

L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
	maxZoom: 19,
	attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>'
}).addTo(mymap);

var geojson = L.geoJson().addTo(mymap);

function updateMap() {
	var bounds = mymap.getBounds();

	atomic.get('/api/stations?n=' + bounds.getNorth() + "&s=" + bounds.getSouth()
			+ "&w=" + bounds.getWest() + "&e=" + bounds.getEast() + "&z=" + mymap.getZoom())
		.success(function(data) {
			geojson.clearLayers();
			geojson.addData(data);
		})
		.error(function (data, xhr) {
			console.log(xhr.status);
		});
}

mymap.on('zoomstart', function() { geojson.clearLayers(); });
mymap.on('dragend', updateMap);
mymap.on('zoomend', updateMap);
updateMap();