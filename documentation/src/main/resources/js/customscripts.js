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

    $("#dev-warning").hide();
    changeSelectedDocVersionDropdownSelection($("#docVersion")[0]);
    $("#docVersion").change(changeSelectedDocVersion);
});

function changeSelectedDocVersionDropdownSelection(element) {
    var pathName = window.location.pathname;

    if (element) {
        var versionOptions = element.options;
        for (var i = 0; i < versionOptions.length; i++) {
            var versionValue = versionOptions[i].value;
            if ((versionValue !== "") && pathName.startsWith("/ditto/"+versionValue+"/")) {
                $("#docVersion").val(versionValue).change();
                return;
            }
        }
        // fallback: dev with "empty" version value:
        $("#docVersion").val("").change();
        $("#dev-warning").show();
    }
}

function changeSelectedDocVersion() {
    var versionValue = $('#docVersion').val();
    var remainingPath = window.location.pathname.replace("/ditto/", "/");
    remainingPath = remainingPath.startsWith("/") ? remainingPath.substr(1) : remainingPath;
    var versionMatch = remainingPath.match("([0-9].[0-9])/(.*)");
    if (versionValue === "" && !versionMatch) {
        // do nothing, we're already on the correct "dev" version
    } else if (versionMatch && (versionValue === versionMatch[1])) {
        // do nothing, we're already on the correct version
    } else {
        if (versionValue === "" && versionMatch) {
            window.location.pathname = "ditto/" + versionMatch[2];
        } else if (versionValue === "") {
            window.location.pathname = "ditto/" + remainingPath;
        } else if (versionMatch) {
            window.location.pathname = "ditto/" + versionValue + "/" + versionMatch[2];
        } else {
            window.location.pathname = "ditto/" + versionValue + "/" + remainingPath;
        }
    }
}
