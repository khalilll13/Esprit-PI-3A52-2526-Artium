import './bootstrap.js';
import '@hotwired/turbo';
/*
 * Welcome to your app's main JavaScript file!
 *
 * This file will be included onto the page via the importmap() Twig function,
 * which should already be in your base.html.twig.
 */
import './styles/app.css';

console.log('This log comes from assets/app.js - welcome to AssetMapper! 🎉');

document.addEventListener('keydown', (event) => {
	const textarea = event.target;
	if (!textarea || textarea.tagName !== 'TEXTAREA') {
		return;
	}

	if (textarea.name !== 'contenu') {
		return;
	}

	if (event.key === 'Enter' && !event.shiftKey) {
		const form = textarea.closest('form');
		if (form) {
			event.preventDefault();
			form.requestSubmit();
		}
	}
});

document.addEventListener('turbo:submit-end', (event) => {
	const form = event.target;
	if (!form || form.tagName !== 'FORM') {
		return;
	}

	if (!form.dataset.turboFrame || !event.detail?.success) {
		return;
	}

	if (!form.action.includes('/commentaire')) {
		return;
	}

	if (!form.querySelector('.comment-submit-button')) {
		return;
	}

	const textarea = form.querySelector('textarea[name="contenu"]');
	if (textarea) {
		textarea.value = '';
	}
});

const cleanupBootstrapOverlays = () => {
	document.querySelectorAll('.modal-backdrop, .offcanvas-backdrop').forEach((el) => el.remove());
	document.body.classList.remove('modal-open');
	document.body.style.removeProperty('padding-right');
	document.body.style.removeProperty('overflow');
	document.querySelectorAll('.modal.show').forEach((el) => {
		el.classList.remove('show');
		el.setAttribute('aria-hidden', 'true');
		el.removeAttribute('aria-modal');
		el.style.display = 'none';
	});
	document.querySelectorAll('.offcanvas.show').forEach((el) => {
		el.classList.remove('show');
		el.setAttribute('aria-hidden', 'true');
		el.removeAttribute('aria-modal');
	});
};

document.addEventListener('turbo:before-cache', cleanupBootstrapOverlays);

const debounce = (fn, delay = 400) => {
	let timer = null;
	return (...args) => {
		if (timer) {
			clearTimeout(timer);
		}
		timer = setTimeout(() => fn(...args), delay);
	};
};

const initAiTicketEstimate = () => {
	if (window.__aiTicketEstimateInit) {
		return;
	}
	window.__aiTicketEstimateInit = true;

	const offcanvas = document.getElementById('offcanvasCreateEvent');
	if (!offcanvas) {
		return;
	}

	const badge = document.getElementById('ai-ticket-estimate');
	if (!badge) {
		return;
	}

	const form = offcanvas.querySelector('form');
	if (!form) {
		return;
	}

	const getFieldValue = (selector) => {
		const field = form.querySelector(selector);
		return field ? field.value : '';
	};

	const setBadge = (text, state = 'info') => {
		badge.textContent = text;
		badge.classList.remove('bg-info', 'bg-warning', 'bg-success', 'bg-danger');
		badge.classList.add(`bg-${state}`);
		if (state === 'info') {
			badge.classList.add('text-dark');
		} else {
			badge.classList.remove('text-dark');
		}
	};

	const buildPayload = () => ({
		titre: getFieldValue('[name$="[titre]"]'),
		description: getFieldValue('[name$="[description]"]'),
		type: getFieldValue('[name$="[type]"]'),
		capacite_max: getFieldValue('[name$="[capacite_max]"]'),
		prix_ticket: getFieldValue('[name$="[prix_ticket]"]'),
		date_debut: getFieldValue('[name$="[date_debut]"]'),
		date_fin: getFieldValue('[name$="[date_fin]"]'),
		galerie: getFieldValue('[name$="[galerie]"]'),
	});

	const shouldEstimate = (payload) => {
		return payload.titre.trim() !== '' || payload.description.trim() !== '' || payload.type !== '';
	};

	const requestEstimate = async () => {
		const payload = buildPayload();
		if (!shouldEstimate(payload)) {
			setBadge(badge.dataset.defaultText || 'Estimation IA: --', 'info');
			return;
		}

		setBadge('Estimation IA: ...', 'warning');

		try {
			const response = await fetch('/artiste-evenements/estimate-tickets', {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
				},
				body: JSON.stringify(payload),
			});

			const data = await response.json();
			if (!data.ok) {
				setBadge('Estimation IA: indisponible', 'danger');
				return;
			}

			const confidenceLabel = data.confidence === 'high'
				? 'forte'
				: data.confidence === 'low'
					? 'faible'
					: 'moyenne';

			setBadge(`Estimation IA: ${data.estimate} tickets (${confidenceLabel})`, 'success');
		} catch (error) {
			setBadge('Estimation IA: erreur', 'danger');
		}
	};

	const debouncedEstimate = debounce(requestEstimate, 500);

	form.addEventListener('input', debouncedEstimate);
	form.addEventListener('change', debouncedEstimate);
};

document.addEventListener('DOMContentLoaded', initAiTicketEstimate);
