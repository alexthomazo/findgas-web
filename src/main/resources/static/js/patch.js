var patch = {

	start: function(modal, stationId) {
		patch.stationId = stationId;
		patch.initModal(modal);
		patch.loadStation();
		patch.modal.modal();
	},

	initModal: function(modal) {
		if (patch.modal) return;
		patch.modal = modal;
		$("#patchSetPoint").click(patch.startSetPoint);
		$('#formPatch').submit(patch.patchStation);
	},

	loadStation: function() {
		if (patch.stationId) {
			$.get('/api/stations/' + patch.stationId).done(patch.fillForm)
			.fail(function() {
				displayError("Impossible de charger la station, merci de réessayer dans quelques instants.");
			})
		} else {
			patch.fillForm({});
		}
	},

	fillForm: function(station) {
		$('#patchStName').val(station.name);
		$('#patchLocation').val(station.location);
		$('#patchAddress').val(station.address);
		$('#patchCp').val(station.cp);
		$('#patchCity').val(station.city);
	},

	patchStation: function(e) {
		e.preventDefault();
		var saveBtn = $('#patchSaveBtn');
		saveBtn.prop('disabled', true);
		
		var form = {
			stationId: patch.stationId,
			stationName: $('#patchStName').val(),
			location: $('#patchLocation').val(),
			address: $('#patchAddress').val(),
			cp: $('#patchCp').val(),
			city: $('#patchCity').val(),
			comment: $('#patchComment').val(),
			name: $('#patchName').val()
		};

		function displayError(err) {
			patch.showMsg("Une erreur est survenue lors de l'enregistrement, " + err, "alert-danger")
		}

		$.post({
			url: "/api/patch",
			data: JSON.stringify(form),
			contentType: "application/json"
		})
		.done(function(data) {
			if (data == 'ok') {
				patch.showMsg("La demande a été prise en compte, elle sera traitée dans quelques instants, merci :)",
					"alert-success");

				setTimeout(function() {
					patch.modal.modal('hide');
					saveBtn.prop('disabled', false);
				}, 1500);

			} else {
				displayError("Une erreur est survenue lors de l'enregistrement : " + data);
				saveBtn.prop('disabled', false);
			}
		})
		.fail(function() {
			displayError("Une erreur est survenue lors de l'enregistrement, veuillez réessayer plus tard.");
			saveBtn.prop('disabled', false);
		})
	},

	showMsg: function(msg, background) {
		var pMsg = $('#patchMsg');
		pMsg.addClass("alert " + background);
		pMsg.text(msg);
	},

	startSetPoint: function(e) {
		e.preventDefault();
		mymap.on('click', patch.clickSetPoint);
		$('#map').addClass('pointer');
		patch.modal.modal('hide');
	},

	clickSetPoint: function(e) {
		patch.modal.modal();
		mymap.off('click', patch.clickSetPoint);
		$('#map').removeClass('pointer');
		$('#patchLocation').val(e.latlng.lat + "," + e.latlng.lng);
		$.get('http://api-adresse.data.gouv.fr/reverse/', {lat: e.latlng.lat, lng: e.latlng.lng}, function(res) {
			if (res.features && res.features.length && res.features.length > 0) {
				var address = res.features[0].properties;
				$('#patchAddress').val(address.name);
				$('#patchCp').val(address.postcode);
				$('#patchCity').val(address.city);
			}
		})
	}

};