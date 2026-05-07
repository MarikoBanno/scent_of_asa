document.addEventListener("DOMContentLoaded", () => {
  const confirmationStorageKey = "scent_of_asa_public_reservation";
  const guestCountSelect = document.querySelector("#guest-count");
  const timeSlotSelect = document.querySelector("#time-slot");
  const guideEnglishRadio = document.querySelector("#guide-english");
  const guideJapaneseRadio = document.querySelector("#guide-japanese");
  const priceItemLabel = document.querySelector("#price-item-label");
  const priceItemAmount = document.querySelector("#price-item-amount");
  const serviceFeeAmount = document.querySelector("#service-fee-amount");
  const priceTotalAmount = document.querySelector("#price-total-amount");
  const confirmationDate = document.querySelector("#confirmation-date");
  const confirmationTime = document.querySelector("#confirmation-time");
  const confirmationGuests = document.querySelector("#confirmation-guests");
  const confirmationLanguage = document.querySelector("#confirmation-language");
  const monthLabel = document.querySelector(".calendar-nav span");
  const prevButton = document.querySelector(".calendar-nav button:first-child");
  const nextButton = document.querySelector(".calendar-nav button:last-child");
  const submitButton = document.querySelector("#booking-submit") || document.querySelector(".price-card .btn-primary");
  const isJapanesePage = document.body.classList.contains("lang-ja");
  const unitPrice = 12000;
  const taxRate = 0.1;
  const today = new Date();
  const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const currentMonthStart = new Date(today.getFullYear(), today.getMonth(), 1);
  const monthCursor = new Date(currentMonthStart);

  const calendarCards = {
    english: document.querySelector(".calendar-english"),
    japanese: document.querySelector(".calendar-japanese"),
  };

  if (!guestCountSelect || !timeSlotSelect || !calendarCards.english || !calendarCards.japanese) {
    return;
  }

  const messages = isJapanesePage
    ? {
        paymentTitle: "クレジットカード決済",
        paymentLead: "カード情報は Stripe の安全な入力フォームで処理され、SCENT OF ASA のサーバーには保存されません。",
        paymentHint: "日付・時間・人数を選ぶと、ここにカード入力フォームが表示されます。",
        paymentReady: "テストモードのカード入力フォームを読み込みました。",
        paymentLoading: "安全な決済フォームを準備しています...",
        paymentError: "決済フォームの準備に失敗しました。内容を確認してもう一度お試しください。",
        processingPayment: "カード決済を処理しています...",
        submittingReservation: "決済確認後に予約を作成しています...",
        redirecting: "予約が完了しました。確認画面へ移動しています...",
        retryNetwork: "通信に失敗しました。少し時間をおいてもう一度お試しください。",
        paymentRequired: "カード情報を入力してください。",
        paymentIncomplete: "カード情報の入力が完了していません。",
        paymentNotSucceeded: "決済が完了しませんでした。カード情報を確認してもう一度お試しください。",
        noDate: "予約日を選択してください。",
        noTime: "時間帯を選択してください。",
        noGuestCount: "人数を選択してください。",
        noFirstName: "名を入力してください。",
        noLastName: "姓を入力してください。",
        noEmail: "メールアドレスを入力してください。",
        invalidEmail: "有効なメールアドレスを入力してください。",
        noPhone: "電話番号を入力してください。",
        paymentMismatch: "決済内容が最新の予約内容と一致しません。もう一度お試しください。",
        noSlots: "選択できる時間帯がありません",
        noGuestOptions: "選択できる人数がありません",
        notSelected: "未選択",
        monthLoadError: "空き状況の読み込みに失敗しました。",
        closedConflict: "選択した日時の予約受付は終了しました。別の日程をお選びください。",
        slotConflict: "選択した枠は直前に埋まりました。別の日時をお選びください。",
        paymentReused: "この決済はすでに予約に使用されています。",
      }
    : {
        paymentTitle: "Credit Card Payment",
        paymentLead: "Card details are handled by Stripe's secure Payment Element and never stored on the SCENT OF ASA server.",
        paymentHint: "Select your date, time, and guest count to load the secure card form here.",
        paymentReady: "The secure test-mode card form is ready.",
        paymentLoading: "Preparing the secure payment form...",
        paymentError: "We could not prepare the payment form. Please review your booking details and try again.",
        processingPayment: "Processing your card payment...",
        submittingReservation: "Payment confirmed. Creating your reservation...",
        redirecting: "Reservation complete. Redirecting to the confirmation page...",
        retryNetwork: "We couldn't complete the request. Please wait a moment and try again.",
        paymentRequired: "Please enter your card details.",
        paymentIncomplete: "Your card details are incomplete.",
        paymentNotSucceeded: "The payment did not complete. Please review your card details and try again.",
        noDate: "Please select a reservation date.",
        noTime: "Please select a time slot.",
        noGuestCount: "Please select the number of guests.",
        noFirstName: "Please enter the first name.",
        noLastName: "Please enter the last name.",
        noEmail: "Please enter an email address.",
        invalidEmail: "Please enter a valid email address.",
        noPhone: "Please enter a phone number.",
        paymentMismatch: "The payment no longer matches the latest reservation details. Please try again.",
        noSlots: "No slots available",
        noGuestOptions: "No guest count available",
        notSelected: "Not selected",
        monthLoadError: "Failed to load availability.",
        closedConflict: "Reservations for the selected date have already closed. Please choose a later date.",
        slotConflict: "That slot just became unavailable. Please choose another date or time.",
        paymentReused: "This payment has already been used for a reservation.",
      };

  const contactForm = document.querySelector("#contact .booking-form");
  const contactTextInputs = contactForm ? [...contactForm.querySelectorAll('input[type="text"]')] : [];
  const firstNameInput = document.querySelector("#customer-first-name") || contactTextInputs[0] || null;
  const lastNameInput = document.querySelector("#customer-last-name") || contactTextInputs[1] || null;
  const emailInput = document.querySelector("#customer-email") || (contactForm ? contactForm.querySelector('input[type="email"]') : null);
  const phoneInput = document.querySelector("#customer-phone") || (contactForm ? contactForm.querySelector('input[type="tel"]') : null);
  const notesInput = document.querySelector("#customer-notes") || (contactForm ? contactForm.querySelector("textarea") : null);

  const stripeState = {
    stripe: null,
    elements: null,
    paymentElement: null,
    clientSecret: null,
    paymentIntentId: null,
    signature: null,
  };

  const slotDisplayLabels = {
    "11:00": "11:00 - 12:30",
    "13:00": "13:00 - 14:30",
    "15:30": "15:30 - 17:00",
  };

  const availabilityByLanguage = {
    english: null,
    japanese: null,
  };
  const selectedDates = {
    english: null,
    japanese: null,
  };
  const selectedSlots = {
    english: null,
    japanese: null,
  };

  let isSubmitting = false;
  let paymentRefreshTimer = null;
  const submitButtonDefaultLabel = submitButton ? submitButton.textContent : "";
  const submitStatus = createInlineStatus(submitButton);
  const paymentUi = setupPaymentSection();

  if (submitButton && submitButton.tagName === "A") {
    submitButton.setAttribute("href", "#");
  }

  function createInlineStatus(anchor) {
    if (!anchor || !anchor.parentElement) {
      return null;
    }
    const status = document.createElement("p");
    status.className = "mini-note";
    status.style.marginTop = "10px";
    status.style.minHeight = "1.4em";
    status.style.color = "var(--muted)";
    anchor.insertAdjacentElement("afterend", status);
    return status;
  }

  function setupPaymentSection() {
    const paymentForms = [...document.querySelectorAll(".panel-block .booking-form")];
    const legacyPaymentForm = paymentForms[paymentForms.length - 1];
    const paymentBlock = legacyPaymentForm?.closest(".panel-block");
    if (!legacyPaymentForm || !paymentBlock) {
      return null;
    }

    legacyPaymentForm.innerHTML = `
      <div class="field">
        <span>${messages.paymentTitle}</span>
        <p class="mini-note" id="payment-element-lead" style="margin-top:8px;">${messages.paymentLead}</p>
      </div>
      <div class="field">
        <div id="payment-element-shell" style="border:1px solid rgba(32,29,24,0.14); border-radius:18px; background:#fffdfa; padding:16px;">
          <div id="payment-element-placeholder" class="mini-note">${messages.paymentHint}</div>
          <div id="payment-element" style="display:none;"></div>
        </div>
      </div>
      <p id="payment-element-status" class="mini-note" style="min-height:1.4em;"></p>
    `;

    return {
      block: paymentBlock,
      placeholder: paymentBlock.querySelector("#payment-element-placeholder"),
      mount: paymentBlock.querySelector("#payment-element"),
      status: paymentBlock.querySelector("#payment-element-status"),
    };
  }

  function setPaymentStatus(message = "", isError = false) {
    if (!paymentUi?.status) {
      return;
    }
    paymentUi.status.textContent = message;
    paymentUi.status.style.color = isError ? "#9f403d" : "var(--muted)";
  }

  function setPaymentPlaceholder(message) {
    if (!paymentUi?.placeholder || !paymentUi?.mount) {
      return;
    }
    paymentUi.placeholder.textContent = message;
    paymentUi.placeholder.style.display = "";
    paymentUi.mount.style.display = "none";
  }

  function showPaymentElement() {
    if (!paymentUi?.placeholder || !paymentUi?.mount) {
      return;
    }
    paymentUi.placeholder.style.display = "none";
    paymentUi.mount.style.display = "";
  }

  function isBeforeCurrentMonth() {
    return monthCursor.getFullYear() < currentMonthStart.getFullYear()
      || (monthCursor.getFullYear() === currentMonthStart.getFullYear() && monthCursor.getMonth() < currentMonthStart.getMonth());
  }

  function isPastDate(isoDate) {
    return new Date(`${isoDate}T00:00:00`).getTime() < todayStart.getTime();
  }

  function isBookingClosedDay(dayData) {
    return !!dayData?.bookingClosed;
  }

  function toLocalIsoDate(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
  }

  function normalizeOptional(value) {
    return value && value.trim() ? value.trim() : null;
  }

  function buildCustomerName() {
    return [firstNameInput?.value?.trim(), lastNameInput?.value?.trim()]
      .filter(Boolean)
      .join(" ")
      .trim();
  }

  function buildReservationPayload(paymentIntentId = null) {
    const activeGuide = getActiveGuide();
    const selectedDate = selectedDates[activeGuide];
    return {
      reservationDate: selectedDate ? toLocalIsoDate(selectedDate) : null,
      timeSlot: selectedSlots[activeGuide] || null,
      guideLanguage: getGuideApiLanguage(activeGuide),
      guestCount: getGuestCount(),
      customerName: buildCustomerName(),
      customerEmail: emailInput?.value?.trim() || "",
      customerPhone: normalizeOptional(phoneInput?.value || ""),
      notes: normalizeOptional(notesInput?.value || ""),
      paymentIntentId,
    };
  }

  function isValidEmail(value) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  }

  function getConfirmationPageUrl() {
    return isJapanesePage ? "confirmation-ja.html" : "confirmation.html";
  }

  function setSubmitState(submitting, message = "", isError = false) {
    isSubmitting = submitting;
    if (submitButton) {
      submitButton.setAttribute("aria-disabled", submitting ? "true" : "false");
      submitButton.textContent = submitting
        ? (isJapanesePage ? "処理中..." : "Processing...")
        : submitButtonDefaultLabel;
      submitButton.style.pointerEvents = submitting ? "none" : "";
      submitButton.style.opacity = submitting ? "0.72" : "";
    }
    if (submitStatus) {
      submitStatus.textContent = message;
      submitStatus.style.color = isError ? "#9f403d" : "var(--muted)";
    }
  }

  function getConflictMessage(message) {
    if (message === "The selected slot is no longer available.") {
      return messages.slotConflict;
    }
    if (message === "Reservations for the selected date have already closed.") {
      return messages.closedConflict;
    }
    if (message === "This payment has already been used for a reservation.") {
      return messages.paymentReused;
    }
    if (message === "The payment details do not match the selected reservation."
      || message === "The payment amount no longer matches this reservation."
      || message === "The payment currency no longer matches this reservation.") {
      return messages.paymentMismatch;
    }
    return message;
  }

  function getValidationMessage(payload) {
    if (!payload.reservationDate) return messages.noDate;
    if (!payload.timeSlot || timeSlotSelect.disabled) return messages.noTime;
    if (!payload.guestCount || guestCountSelect.disabled) return messages.noGuestCount;
    if (!firstNameInput?.value?.trim()) return messages.noFirstName;
    if (!lastNameInput?.value?.trim()) return messages.noLastName;
    if (!payload.customerEmail) return messages.noEmail;
    if (!isValidEmail(payload.customerEmail)) return messages.invalidEmail;
    if (!payload.customerPhone) return messages.noPhone;
    return null;
  }

  function getGuestCount() {
    const match = guestCountSelect.value.match(/\d+/);
    return match ? Number(match[0]) : 1;
  }

  function getActiveGuide() {
    return guideJapaneseRadio?.checked ? "japanese" : "english";
  }

  function getGuideApiLanguage(guideKey) {
    return guideKey === "english" ? "en" : "ja";
  }

  function getGuideSummaryLabel(guideKey) {
    if (isJapanesePage) {
      return guideKey === "english" ? "英語" : "日本語";
    }
    return guideKey === "english" ? "English" : "Japanese";
  }

  function getGuidePriceLabel(guideKey, guestCount) {
    if (isJapanesePage) {
      return guideKey === "english"
        ? `Workshop (EN) × ${guestCount}名`
        : `Workshop (JP) × ${guestCount}名`;
    }
    return guideKey === "english"
      ? `Workshop (EN) × ${guestCount} ${guestCount === 1 ? "guest" : "guests"}`
      : `Workshop (JP) × ${guestCount} ${guestCount === 1 ? "guest" : "guests"}`;
  }

  function getSummaryGuestLabel(guestCount) {
    return isJapanesePage
      ? `${guestCount}名`
      : `${guestCount} ${guestCount === 1 ? "Guest" : "Guests"}`;
  }

  function getTimeDisplayLabel(timeSlot) {
    return slotDisplayLabels[timeSlot] || timeSlot;
  }

  function getSlotOptionLabel(slot) {
    const timeLabel = getTimeDisplayLabel(slot.timeSlot);
    if (slot.status === "LIMITED") {
      return isJapanesePage ? `${timeLabel} (残りわずか)` : `${timeLabel} (Limited)`;
    }
    return timeLabel;
  }

  function formatSummaryDate(date) {
    if (!date) {
      return messages.notSelected;
    }
    if (isJapanesePage) {
      return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`;
    }
    return new Intl.DateTimeFormat("en-US", {
      year: "numeric",
      month: "long",
      day: "numeric",
    }).format(date);
  }

  function formatPrice(amount) {
    if (isJapanesePage) {
      return `${amount.toLocaleString("ja-JP")}円`;
    }
    return `JPY ${amount.toLocaleString("en-US")}`;
  }

  function getSelectedDayData(guideKey) {
    const selectedDate = selectedDates[guideKey];
    const availability = availabilityByLanguage[guideKey];
    if (!selectedDate || !availability) {
      return null;
    }
    const isoDate = toLocalIsoDate(selectedDate);
    return availability.days.find((day) => day.date === isoDate) || null;
  }

  function getReservableSlots(guideKey) {
    const dayData = getSelectedDayData(guideKey);
    if (!dayData) {
      return [];
    }
    return dayData.slots.filter((slot) => slot.status === "OPEN" || slot.status === "LIMITED");
  }

  function syncSelectedDateForMonth(guideKey) {
    const availability = availabilityByLanguage[guideKey];
    if (!availability) {
      selectedDates[guideKey] = null;
      selectedSlots[guideKey] = null;
      return;
    }

    const currentSelection = selectedDates[guideKey];
    if (currentSelection) {
      const isoDate = toLocalIsoDate(currentSelection);
      const existingDay = availability.days.find((day) => day.date === isoDate);
      if (existingDay && !isPastDate(existingDay.date) && existingDay.slots.some((slot) => slot.available)) {
        return;
      }
    }

    const firstAvailableDay = availability.days.find((day) => !isPastDate(day.date) && day.slots.some((slot) => slot.available));
    selectedDates[guideKey] = firstAvailableDay ? new Date(`${firstAvailableDay.date}T00:00:00`) : null;
    selectedSlots[guideKey] = null;
  }

  function syncSelectedSlot(guideKey) {
    const reservableSlots = getReservableSlots(guideKey);
    if (reservableSlots.length === 0) {
      selectedSlots[guideKey] = null;
      return;
    }

    const currentSelection = selectedSlots[guideKey];
    const matchingSlot = reservableSlots.find((slot) => slot.timeSlot === currentSelection);
    selectedSlots[guideKey] = matchingSlot ? matchingSlot.timeSlot : reservableSlots[0].timeSlot;
  }

  function updateGuestCountOptions() {
    const activeGuide = getActiveGuide();
    const reservableSlots = getReservableSlots(activeGuide);
    const selectedSlot = reservableSlots.find((slot) => slot.timeSlot === selectedSlots[activeGuide]);

    guestCountSelect.innerHTML = "";

    if (!selectedSlot) {
      const option = document.createElement("option");
      option.textContent = messages.noGuestOptions;
      guestCountSelect.append(option);
      guestCountSelect.disabled = true;
      return;
    }

    guestCountSelect.disabled = false;
    for (let guestCount = 1; guestCount <= selectedSlot.remainingCapacity; guestCount += 1) {
      const option = document.createElement("option");
      option.value = isJapanesePage ? `${guestCount}名` : `${guestCount} ${guestCount === 1 ? "Guest" : "Guests"}`;
      option.textContent = option.value;
      guestCountSelect.append(option);
    }
  }

  function updateTimeSlotOptions() {
    const activeGuide = getActiveGuide();
    const reservableSlots = getReservableSlots(activeGuide);

    timeSlotSelect.innerHTML = "";

    if (reservableSlots.length === 0) {
      const option = document.createElement("option");
      option.textContent = messages.noSlots;
      timeSlotSelect.append(option);
      timeSlotSelect.disabled = true;
      updateGuestCountOptions();
      updatePriceSummary();
      invalidatePaymentIntent(messages.paymentHint);
      return;
    }

    timeSlotSelect.disabled = false;
    syncSelectedSlot(activeGuide);

    reservableSlots.forEach((slot) => {
      const option = document.createElement("option");
      option.value = slot.timeSlot;
      option.textContent = getSlotOptionLabel(slot);
      option.selected = slot.timeSlot === selectedSlots[activeGuide];
      timeSlotSelect.append(option);
    });

    updateGuestCountOptions();
    updatePriceSummary();
    schedulePaymentIntentRefresh();
  }

  function updateConfirmationSummary() {
    const activeGuide = getActiveGuide();
    const guestCount = getGuestCount();
    const selectedDate = selectedDates[activeGuide];

    if (confirmationDate) {
      confirmationDate.textContent = formatSummaryDate(selectedDate);
    }
    if (confirmationTime) {
      confirmationTime.textContent = timeSlotSelect.disabled ? messages.notSelected : getTimeDisplayLabel(selectedSlots[activeGuide]);
    }
    if (confirmationGuests) {
      confirmationGuests.textContent = timeSlotSelect.disabled ? messages.notSelected : getSummaryGuestLabel(guestCount);
    }
    if (confirmationLanguage) {
      confirmationLanguage.textContent = getGuideSummaryLabel(activeGuide);
    }
  }

  function updatePriceSummary() {
    const guestCount = timeSlotSelect.disabled ? 0 : getGuestCount();
    const activeGuide = getActiveGuide();
    const subtotal = guestCount * unitPrice;
    const consumptionTax = Math.round(subtotal * taxRate);
    const total = subtotal + consumptionTax;

    if (priceItemLabel) {
      priceItemLabel.textContent = getGuidePriceLabel(activeGuide, guestCount || 0);
    }
    if (priceItemAmount) {
      priceItemAmount.textContent = formatPrice(subtotal);
    }
    if (serviceFeeAmount) {
      serviceFeeAmount.textContent = formatPrice(consumptionTax);
    }
    if (priceTotalAmount) {
      priceTotalAmount.textContent = formatPrice(total);
    }

    updateConfirmationSummary();
  }

  function updateMonthLabel() {
    if (!monthLabel) {
      return;
    }
    if (isJapanesePage) {
      monthLabel.textContent = `${monthCursor.getFullYear()}年${monthCursor.getMonth() + 1}月`;
      return;
    }
    monthLabel.textContent = new Intl.DateTimeFormat("en-US", {
      year: "numeric",
      month: "long",
    }).format(monthCursor);
  }

  function updateMonthNavigation() {
    if (prevButton) {
      prevButton.disabled = isBeforeCurrentMonth()
        || (monthCursor.getFullYear() === currentMonthStart.getFullYear() && monthCursor.getMonth() === currentMonthStart.getMonth());
    }
  }

  function createMutedDayButton(text) {
    const button = document.createElement("button");
    button.className = "day muted";
    button.type = "button";
    button.textContent = String(text);
    button.disabled = true;
    return button;
  }

  function renderCalendar(guideKey) {
    const card = calendarCards[guideKey];
    const availability = availabilityByLanguage[guideKey];
    if (!card || !availability) {
      return;
    }

    const grid = card.querySelector(".calendar-grid");
    if (!grid) {
      return;
    }

    const year = monthCursor.getFullYear();
    const month = monthCursor.getMonth();
    const firstDay = new Date(year, month, 1).getDay();
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const prevMonthDays = new Date(year, month, 0).getDate();
    const selectedDate = selectedDates[guideKey];

    grid.innerHTML = "";

    for (let i = firstDay - 1; i >= 0; i -= 1) {
      grid.append(createMutedDayButton(prevMonthDays - i));
    }

    for (let day = 1; day <= daysInMonth; day += 1) {
      const button = document.createElement("button");
      const isoDate = `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
      const dayData = availability.days.find((entry) => entry.date === isoDate);
      const hasAvailableSlot = !!dayData && !isPastDate(isoDate) && dayData.slots.some((slot) => slot.available);
      const isPast = isPastDate(isoDate);
      const bookingClosed = isBookingClosedDay(dayData);

      button.className = "day";
      button.type = "button";
      button.textContent = String(day);
      button.dataset.guide = guideKey;
      button.dataset.date = isoDate;

      if (isPast) {
        button.classList.add("muted");
        button.disabled = true;
      } else if (bookingClosed || !hasAvailableSlot) {
        button.classList.add("soldout");
      }

      if (selectedDate && toLocalIsoDate(selectedDate) === isoDate) {
        button.classList.add("selected");
      }

      grid.append(button);
    }

    const totalCells = Math.ceil((firstDay + daysInMonth) / 7) * 7;
    const trailingDays = totalCells - (firstDay + daysInMonth);
    for (let day = 1; day <= trailingDays; day += 1) {
      grid.append(createMutedDayButton(day));
    }
  }

  function renderCalendars() {
    updateMonthLabel();
    updateMonthNavigation();
    renderCalendar("english");
    renderCalendar("japanese");
  }

  async function fetchAvailability(guideKey) {
    const response = await fetch(`/api/public/availability?year=${monthCursor.getFullYear()}&month=${monthCursor.getMonth() + 1}&language=${getGuideApiLanguage(guideKey)}`);
    if (!response.ok) {
      throw new Error(messages.monthLoadError);
    }
    availabilityByLanguage[guideKey] = await response.json();
    syncSelectedDateForMonth(guideKey);
    syncSelectedSlot(guideKey);
  }

  async function loadMonth() {
    await Promise.all([fetchAvailability("english"), fetchAvailability("japanese")]);
    renderCalendars();
    updateTimeSlotOptions();
  }

  function moveMonth(delta) {
    monthCursor.setMonth(monthCursor.getMonth() + delta);
    if (isBeforeCurrentMonth()) {
      monthCursor.setTime(currentMonthStart.getTime());
    }
    loadMonth().catch(() => {
      updateMonthLabel();
      updateMonthNavigation();
      setPaymentStatus(messages.monthLoadError, true);
    });
  }

  function canPreparePaymentIntent() {
    const payload = buildReservationPayload();
    return Boolean(
      payload.reservationDate
      && payload.timeSlot
      && payload.guestCount
      && !timeSlotSelect.disabled
      && !guestCountSelect.disabled
    );
  }

  function getPaymentSignature() {
    const payload = buildReservationPayload();
    return [
      payload.reservationDate || "",
      payload.timeSlot || "",
      payload.guideLanguage || "",
      payload.guestCount || 0,
    ].join("|");
  }

  function invalidatePaymentIntent(placeholderMessage = messages.paymentHint) {
    stripeState.clientSecret = null;
    stripeState.paymentIntentId = null;
    stripeState.signature = null;
    if (stripeState.paymentElement && typeof stripeState.paymentElement.destroy === "function") {
      stripeState.paymentElement.destroy();
    }
    stripeState.paymentElement = null;
    stripeState.elements = null;
    if (paymentUi?.mount) {
      paymentUi.mount.innerHTML = "";
    }
    setPaymentPlaceholder(placeholderMessage);
    setPaymentStatus("");
  }

  async function ensureStripeScript() {
    if (window.Stripe) {
      return;
    }

    await new Promise((resolve, reject) => {
      const existing = document.querySelector('script[data-stripe-js="true"]');
      if (existing) {
        existing.addEventListener("load", resolve, { once: true });
        existing.addEventListener("error", reject, { once: true });
        return;
      }

      const script = document.createElement("script");
      script.src = "https://js.stripe.com/v3/";
      script.async = true;
      script.dataset.stripeJs = "true";
      script.addEventListener("load", resolve, { once: true });
      script.addEventListener("error", reject, { once: true });
      document.head.append(script);
    });
  }

  async function ensurePaymentIntent(forceRefresh = false) {
    if (!paymentUi) {
      return false;
    }
    if (!canPreparePaymentIntent()) {
      invalidatePaymentIntent(messages.paymentHint);
      return false;
    }

    const signature = getPaymentSignature();
    if (!forceRefresh && stripeState.signature === signature && stripeState.paymentElement) {
      return true;
    }

    const payload = buildReservationPayload();
    setPaymentPlaceholder(messages.paymentLoading);
    setPaymentStatus(messages.paymentLoading);

    try {
      const response = await fetch("/api/public/payments/intents", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.message || messages.paymentError);
      }

      const payment = await response.json();
      await ensureStripeScript();

      if (!stripeState.stripe || stripeState.publishableKey !== payment.publishableKey) {
        stripeState.stripe = window.Stripe(payment.publishableKey);
        stripeState.publishableKey = payment.publishableKey;
      }

      if (stripeState.paymentElement && typeof stripeState.paymentElement.destroy === "function") {
        stripeState.paymentElement.destroy();
      }
      if (paymentUi.mount) {
        paymentUi.mount.innerHTML = "";
      }

      stripeState.elements = stripeState.stripe.elements({
        clientSecret: payment.clientSecret,
        appearance: {
          theme: "stripe",
          variables: {
            colorPrimary: "#1a4f4a",
            colorBackground: "#fffdfa",
            colorText: "#201d18",
            colorDanger: "#9f403d",
            borderRadius: "14px",
          },
        },
      });
      stripeState.paymentElement = stripeState.elements.create("payment", {
        layout: "tabs",
      });
      stripeState.paymentElement.mount("#payment-element");
      stripeState.clientSecret = payment.clientSecret;
      stripeState.paymentIntentId = payment.paymentIntentId;
      stripeState.signature = signature;
      showPaymentElement();
      setPaymentStatus(messages.paymentReady);
      return true;
    } catch (error) {
      invalidatePaymentIntent(messages.paymentError);
      setPaymentStatus(getConflictMessage(error.message || messages.paymentError), true);
      return false;
    }
  }

  function schedulePaymentIntentRefresh() {
    if (paymentRefreshTimer) {
      window.clearTimeout(paymentRefreshTimer);
    }
    paymentRefreshTimer = window.setTimeout(() => {
      ensurePaymentIntent(false);
    }, 250);
  }

  async function submitReservation(event) {
    event.preventDefault();

    if (isSubmitting) {
      return;
    }

    const payload = buildReservationPayload();
    const validationMessage = getValidationMessage(payload);
    if (validationMessage) {
      window.alert(validationMessage);
      return;
    }

    const paymentReady = await ensurePaymentIntent(false);
    if (!paymentReady || !stripeState.stripe || !stripeState.elements) {
      window.alert(messages.paymentError);
      return;
    }

    try {
      setSubmitState(true, messages.processingPayment);
      const submitResult = await stripeState.elements.submit();
      if (submitResult?.error) {
        throw new Error(submitResult.error.message || messages.paymentIncomplete);
      }

      const { error, paymentIntent } = await stripeState.stripe.confirmPayment({
        elements: stripeState.elements,
        redirect: "if_required",
        confirmParams: {
          payment_method_data: {
            billing_details: {
              name: payload.customerName,
              email: payload.customerEmail,
              phone: payload.customerPhone || undefined,
            },
          },
        },
      });

      if (error) {
        throw new Error(error.message || messages.paymentNotSucceeded);
      }
      if (!paymentIntent || paymentIntent.status !== "succeeded") {
        throw new Error(messages.paymentNotSucceeded);
      }

      const reservationPayload = buildReservationPayload(paymentIntent.id);
      setSubmitState(true, messages.submittingReservation);
      const response = await fetch("/api/public/reservations", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(reservationPayload),
      });

      if (!response.ok) {
        const apiError = await response.json().catch(() => ({}));
        throw new Error(apiError.message || messages.retryNetwork);
      }

      const reservation = await response.json();
      const confirmationPayload = {
        reservationId: reservation.reservationId ?? reservation.id ?? null,
        reservationCode: reservation.reservationCode || null,
        reservationDate: reservationPayload.reservationDate,
        timeSlot: reservationPayload.timeSlot,
        guideLanguage: reservationPayload.guideLanguage,
        guestCount: reservationPayload.guestCount,
        customerName: reservationPayload.customerName,
        customerEmail: reservationPayload.customerEmail,
        customerPhone: reservationPayload.customerPhone,
        notes: reservationPayload.notes,
        reservationStatus: reservation.status || reservation.reservationStatus || null,
        paymentStatus: paymentIntent.status,
        paymentIntentId: paymentIntent.id,
        completedAt: new Date().toISOString(),
      };
      window.sessionStorage.setItem(confirmationStorageKey, JSON.stringify(confirmationPayload));
      setSubmitState(true, messages.redirecting);
      window.location.href = getConfirmationPageUrl();
    } catch (error) {
      const message = error instanceof TypeError
        ? messages.retryNetwork
        : getConflictMessage(error.message || messages.retryNetwork);
      setSubmitState(false, message, true);
      setPaymentStatus(message, true);
      window.alert(message);
    }
  }

  Object.entries(calendarCards).forEach(([guideKey, card]) => {
    card.addEventListener("click", (event) => {
      const day = event.target.closest(".day");
      if (!day || day.classList.contains("muted") || day.classList.contains("soldout")) {
        return;
      }

      selectedDates[guideKey] = new Date(`${day.dataset.date}T00:00:00`);
      selectedSlots[guideKey] = null;
      syncSelectedSlot(guideKey);
      renderCalendar(guideKey);
      if (getActiveGuide() === guideKey) {
        updateTimeSlotOptions();
      } else {
        schedulePaymentIntentRefresh();
      }
    });
  });

  prevButton?.addEventListener("click", () => moveMonth(-1));
  nextButton?.addEventListener("click", () => moveMonth(1));

  timeSlotSelect.addEventListener("change", () => {
    selectedSlots[getActiveGuide()] = timeSlotSelect.value;
    updateGuestCountOptions();
    updatePriceSummary();
    schedulePaymentIntentRefresh();
  });

  guestCountSelect.addEventListener("change", () => {
    updatePriceSummary();
    schedulePaymentIntentRefresh();
  });

  guideEnglishRadio?.addEventListener("change", () => {
    updateTimeSlotOptions();
    renderCalendars();
  });
  guideJapaneseRadio?.addEventListener("change", () => {
    updateTimeSlotOptions();
    renderCalendars();
  });

  submitButton?.addEventListener("click", submitReservation);

  loadMonth().catch(() => {
    updateMonthLabel();
    updatePriceSummary();
    setPaymentStatus(messages.monthLoadError, true);
  });
});
