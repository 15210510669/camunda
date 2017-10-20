document.querySelector('form').addEventListener('submit', function(event) {
  event.preventDefault();

  var licenseKey = document.querySelector('textarea').value;

  var request = new XMLHttpRequest();
  request.open('POST', '/api/license/validate-and-store');
  request.setRequestHeader('Content-Type', 'text/plain');
  request.addEventListener('readystatechange', function(event) {
    if(event.target.readyState === 4) {
      var response = JSON.parse(event.target.response);
      var alertBox = document.querySelector('.alert');

      alertBox.style.display = 'block';

      if(response.errorMessage) {
        alertBox.setAttribute('class', 'alert alert-danger');
        alertBox.textContent = response.errorMessage;
      } else {
        var message = 'Licensed for ' + response.customerId + '.';

        if (!response.unlimited) {
          var date = new Date(response.validUntil);

          message += ' Valid until ' + date.toUTCString() + '.';
        }

        message += ' You will be redirected to login page shortly. ' +
          '<a href="/">Click here to go to login page immediately</a>';


        alertBox.innerHTML = message;
        alertBox.setAttribute('class', 'alert alert-success');

        window.setTimeout(function() {
          window.location.pathname = '/';
        }, 10000);
      }
    }
  });

  request.send(licenseKey);
});

var request = new XMLHttpRequest();
request.open('GET', '/api/license/validate');
request.addEventListener('readystatechange', function(event) {
  if(event.target.readyState === 4) {
    var response = JSON.parse(event.target.response);
    var alertBox = document.querySelector('.alert');

    alertBox.style.display = 'block';

    if(response.errorMessage) {
      alertBox.setAttribute('class', 'alert alert-danger');
      alertBox.textContent = response.errorMessage;
    } else {
      var message = 'Licensed for ' + response.customerId + '.';

      if (!response.unlimited) {
        var date = new Date(response.validUntil);

        message += ' Valid until ' + date.toUTCString() + '.';
      }

      alertBox.innerHTML = message;
      alertBox.setAttribute('class', 'alert alert-success');
    }
  }
});

request.send();