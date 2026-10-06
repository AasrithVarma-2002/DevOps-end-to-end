// Stops a too-large file before it is sent. The server checks again; this only saves a slow
// upload that would be refused (and a request over the server's limit can't show a friendly error).
document.querySelectorAll('form[data-max-upload]').forEach(function (form) {
    var input = form.querySelector('input[type=file][data-max-bytes]');
    var error = form.querySelector('.upload-error');
    if (!input || !error) return;
    var max = Number(input.dataset.maxBytes);
    function check() {
        var file = input.files && input.files[0];
        var tooBig = file && file.size > max;
        error.hidden = !tooBig;
        error.textContent = tooBig
            ? 'This file is ' + (file.size / 1048576).toFixed(1) + ' MB. The limit is ' + Math.round(max / 1048576) + ' MB; please upload a smaller scan or photo.'
            : '';
        return !tooBig;
    }
    input.addEventListener('change', check);
    form.addEventListener('submit', function (e) { if (!check()) e.preventDefault(); });
});
