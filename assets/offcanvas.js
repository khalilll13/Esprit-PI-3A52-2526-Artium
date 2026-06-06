function applyOffcanvasStyles() {
	var nodes = document.querySelectorAll('.js-offcanvas-style');
	for (var i = 0; i < nodes.length; i += 1) {
		var node = nodes[i];
		if (node.dataset.offcanvasStyled === 'true') {
			continue;
		}
		var width = node.getAttribute('data-offcanvas-width');
		var zIndex = node.getAttribute('data-offcanvas-z-index');
		if (width) {
			node.style.width = width;
		}
		if (zIndex) {
			node.style.zIndex = zIndex;
		}
		node.dataset.offcanvasStyled = 'true';
	}
}

if (document.readyState === 'loading') {
	document.addEventListener('DOMContentLoaded', applyOffcanvasStyles);
} else {
	applyOffcanvasStyles();
}
