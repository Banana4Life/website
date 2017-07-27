$(".project img").each(function () {
    if (!this.complete || typeof this.naturalWidth == "undefined" || this.naturalWidth == 0) {
        $(this).remove();
    }
});
