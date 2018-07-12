$(function () {
    //this script says, if the height of the viewport is greater than 800px, then insert affix class, which makes the
    // nav bar float in a fixed position as your scroll. if you have a lot of nav items, this height may not work for
    // you. var h = $(window).height(); console.log (h); if (h > 800) { $( "#mysidebar" ).attr("class", "nav affix"); }
    // activate tooltips. although this is a bootstrap js function, it must be activated this way in your theme.
    $('[data-toggle="tooltip"]').tooltip({ placement: 'top' });

    anchors.add('h2,h3,h4,h5');

    $(document).ready(function() {
        $("#mysidebar li.subfolders > a").click(function (e) {
            e.preventDefault();
            var theUl = $(this).parent().select("ul");
            if (!theUl.hasClass("open")) {
                theUl.addClass("open");
                theUl.removeClass("closed");
            } else {
                theUl.addClass("closed");
                theUl.removeClass("open");
            }
        });
        $("#collapseAll").click(function(e) {
            e.preventDefault();
            var theLis = $("#mysidebar li.subfolders");
            theLis.addClass("closed");
            theLis.removeClass("open");
        });

        $("#expandAll").click(function(e) {
            e.preventDefault();
            var theLis = $("#mysidebar li.subfolders");
            theLis.addClass("open");
            theLis.removeClass("closed");
        });

    });
});
