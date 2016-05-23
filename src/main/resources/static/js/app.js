var mymap = L.map('map').setView([47.9099, -2.5653], 9);

L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
	maxZoom: 19,
	attribution: '<a href="about.html">A propos de ce site</a> &copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>'
}).addTo(mymap);


function pointToLayer(feature, latlng) {
	var markerOpt = {
		fillColor: "#ff7800",
		color: "#000",
		weight: 1,
		opacity: 1,
		fillOpacity: 0.7
	};

	if (feature.properties.radius) {
		var radius = feature.properties.radius * 1000;
		if (radius < 200) radius = 200;
		return L.circle(latlng, radius, markerOpt);

	} else {
		if (!feature.properties.last_update) {
			markerOpt.fillColor = "#777777";
		} else {
			//check user filter
			if (feature.properties.gas && feature.properties.gas.length > 0) {
				markerOpt.fillColor = "#00DD00";
			} else {
				markerOpt.fillColor = "#FF0000";
			}
		}
		markerOpt.radius = 9;
		return L.circleMarker(latlng, markerOpt);
	}
}

function onFeature(f, layer) {
	var prop = f.properties;
	var nb = prop.nb;
	if (nb) {
		layer.bindPopup(nb + " station" + (nb > 1 ? "s" : "") + " dans cette zone.<br>Zoomer pour voir le détail");
	} else {
		var popup = prop.address + "<br>" + prop.cp + " " + prop.city + "<br>";

		//gaz if station updated
		if (prop.gas && prop.gas.length > 0) {
			popup += "<br><b>Carburants disponibles</b> :<ul>"
			for (var i = 0; i < prop.gas.length; i++) {
				popup += "<li>" + mapGas(prop.gas[i]) + "</li>";
			}
			popup += "</ul>";
		} else if (prop.last_ago) {
			popup += "<br><b>A sec</b><br>"
		} else {
			popup += "<br><b>Pas de données</b><br>"
		}

		if (prop.comment) {
			popup += "<br><b>Précisions :</b><br>" + prop.comment + "<br>";
		}

		if (prop.last_ago) {
			popup += "<br>Mis à jour " + prop.last_ago + "<br>";
		}
		if (prop.last_name) {
			popup += "Par <i>" + prop.last_name + "</i><br>";
		}

		popup += "<br><button class='btn btn-primary' onclick=\"updateStation('" + prop.id + "')\">Proposer une mise à jour</button><br>"

		popup += "<br>" + "<small>" + prop.id + "</small>";
		layer.bindPopup(popup);
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

	curQuery = $.get('/api/stations?n=' + bounds.getNorth() + "&s=" + bounds.getSouth()
		+ "&w=" + bounds.getWest() + "&e=" + bounds.getEast() + "&z=" + mymap.getZoom())
		.done(function(data) {
			geojson.clearLayers();
			geojson.addData(data);
		})
		.fail(function (xhr, status) {
			if (status == "abort") return;

			if (xhr.status == 502) {
				showErrorModal("Maintenance en cours", "Le site est en cours de maintenance, " +
					"merci de réessayer dans quelques instants.")
			} else {
				showErrorModal("Erreur", "Une erreur est survenue pendant la récupération des données, " +
					"merci de réessayer dans quelques instants.");
			}
		})
		.always(function() {
			curQuery = undefined;
		});
}

function showErrorModal(title, desc) {
	function displayMsg(elem) {
		elem.find('.modal-title').text(title);
		elem.find('.modal-body').text(desc);
		elem.modal();
	}

	var $errorModal = $('#errorModal');
	if ($errorModal.length > 0) {
		displayMsg($errorModal);
	} else {
		$('#errorLoad').load('modal-error.html', function() {
			displayMsg($('#errorModal'));
		});
	}
}

mymap.on('zoomstart', function() { geojson.clearLayers(); });
mymap.on('dragend', updateMap);
mymap.on('zoomend', updateMap);
mymap.on('locationfound', updateMap);
mymap.on('locationerror', updateMap);
updateMap();

mymap.locate({setView: true, maxZoom: 16});

var msg = $('#message');
var updateStationId;
function updateStation(stationId) {
	updateStationId = stationId;
	msg.text("");
	msg.removeClass();
	$('#submitModal').modal();
}

var formComment = $("#formComment");
formComment.submit(function(event) {
	event.preventDefault();

	var gas = [];
	formComment.find("input:checkbox:checked").each(function(){
		gas.push($(this).val());
	});

	var comment = {
		comment: $("#comment").val(),
		name: $("#name").val(),
		gas: gas
	};

	var saveBtn = $('#saveBtn');
	saveBtn.attr("disabled", "disabled");

	function displayError() {
		msg.addClass("alert alert-danger");
		msg.text("Une erreur est survenue lors de l'enregistrement, veuillez réessayer.");
	}

	$.post({
		url: "/api/comments?stationId=" + updateStationId,
		data: JSON.stringify(comment),
		contentType: "application/json"
	})
	.done(function(data) {
		if (data == '"ok"') {
			msg.addClass("alert alert-success");
			msg.text("Mise à jour enregistrée, merci :)");

			setTimeout(function() {
				updateMap();
				$('#submitModal').modal('hide');
			}, 1500);

		} else {
			displayError();
		}
	})
	.fail(function() {
		displayError();
	})
	.always(function () {
		saveBtn.removeAttr("disabled");
	});
});

function mapGas(gas) {
	switch (gas) {
		case "go": return "Gazole";
		case "sp95": return "SP95";
		case "sp98": return "SP98";
	}
	return gas;
}