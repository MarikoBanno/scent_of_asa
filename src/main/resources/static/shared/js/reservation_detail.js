(() => {
  const feedback = document.querySelector("#reservation-status-feedback");
  const dateInput = document.querySelector("#reservation-filter-date");
  const customerNameInput = document.querySelector("#reservation-filter-name");
  const languageSelect = document.querySelector("#reservation-filter-language");
  const searchButton = document.querySelector("#reservation-filter-search");
  const exportButton = document.querySelector("#reservation-export");
  const countBadge = document.querySelector("#reservation-total-count");
  const tableBody = document.querySelector("#reservation-table-body");
  const sortKeySelect = document.querySelector("#reservation-sort-key");
  const sortDirectionButton = document.querySelector("#reservation-sort-direction");
  const modal = document.querySelector("#reservation-modal");
  const modalBody = document.querySelector("#reservation-modal-body");
  const modalCloseButton = document.querySelector("#reservation-modal-close");

  if (!tableBody) {
    return;
  }

  const state = {
    reservations: [],
    sortKey: "reservationDate",
    sortDirection: "desc",
  };

  const statusLabels = {
    PENDING: "調整中",
    CONFIRMED: "予約確定",
    PAID: "支払い済み",
    CHECKED_IN: "チェックイン済み",
    CANCELLED: "キャンセル",
    NO_SHOW: "無断キャンセル",
  };

  const statusClasses = {
    PENDING: "is-waiting",
    CONFIRMED: "is-confirmed",
    PAID: "is-paid",
    CHECKED_IN: "is-done",
    CANCELLED: "is-alert",
    NO_SHOW: "is-stop",
  };

  const paymentLabels = {
    SUCCEEDED: "支払い成功",
    REQUIRES_PAYMENT_METHOD: "未払い",
    REQUIRES_ACTION: "追加認証待ち",
    PROCESSING: "処理中",
    CANCELLED: "キャンセル",
    UNSPECIFIED: "-",
  };

  const paymentClasses = {
    SUCCEEDED: "is-succeeded",
    REQUIRES_PAYMENT_METHOD: "is-unpaid",
    REQUIRES_ACTION: "is-action",
    PROCESSING: "is-processing",
    CANCELLED: "is-cancelled",
    UNSPECIFIED: "is-unspecified",
  };

  const statusOptions = ["PENDING", "CONFIRMED", "PAID", "CHECKED_IN", "CANCELLED", "NO_SHOW"];
  const statusOrder = ["PENDING", "CONFIRMED", "PAID", "CHECKED_IN", "CANCELLED", "NO_SHOW"];

  function setFeedback(message, isError = false) {
    if (!feedback) {
      return;
    }
    feedback.textContent = message;
    feedback.style.color = isError ? "#9f403d" : "";
  }

  function buildQuery() {
    const params = new URLSearchParams();
    if (dateInput?.value) {
      params.set("date", dateInput.value);
    }
    if (customerNameInput?.value?.trim()) {
      params.set("customerName", customerNameInput.value.trim());
    }
    if (languageSelect?.value) {
      params.set("guideLanguage", languageSelect.value);
    }
    return params.toString();
  }

  function formatDate(isoDate) {
    if (!isoDate) {
      return "-";
    }
    return String(isoDate).replaceAll("-", "/");
  }

  function formatDateTime(dateTime) {
    if (!dateTime) {
      return "-";
    }
    const value = String(dateTime).replace("T", " ");
    return value.length >= 16 ? value.slice(0, 16) : value;
  }

  function formatTime(timeSlot) {
    if (timeSlot === "11:00") return "11:00 - 12:30";
    if (timeSlot === "13:00") return "13:00 - 14:30";
    if (timeSlot === "15:30") return "15:30 - 17:00";
    return timeSlot || "-";
  }

  function formatLanguage(language) {
    if (language === "ja") return "日本語";
    if (language === "en") return "English";
    return language || "-";
  }

  function formatPaymentStatus(paymentStatus) {
    return paymentLabels[paymentStatus] || paymentStatus || "-";
  }

  function compareValues(left, right) {
    if (left < right) return -1;
    if (left > right) return 1;
    return 0;
  }

  function getStatusRank(status) {
    const index = statusOrder.indexOf(status);
    return index === -1 ? statusOrder.length : index;
  }

  function getSortValue(reservation, sortKey) {
    if (sortKey === "reservationStatus") {
      return getStatusRank(reservation.reservationStatus);
    }
    if (sortKey === "createdAt") {
      return reservation.createdAt || "";
    }
    return reservation.reservationDate || "";
  }

  function getSortedReservations() {
    const multiplier = state.sortDirection === "asc" ? 1 : -1;
    return [...state.reservations].sort((left, right) => {
      const byPrimary = compareValues(
        getSortValue(left, state.sortKey),
        getSortValue(right, state.sortKey)
      );
      if (byPrimary !== 0) {
        return byPrimary * multiplier;
      }
      return compareValues(
        left.reservationCode || "",
        right.reservationCode || ""
      ) * multiplier;
    });
  }

  function createStatusBadge(status) {
    const badge = document.createElement("span");
    badge.className = `admin-badge reservation-status-badge ${statusClasses[status] || ""}`.trim();
    badge.dataset.status = status;
    badge.textContent = statusLabels[status] || status;
    return badge;
  }

  function createPaymentBadge(status) {
    const badge = document.createElement("span");
    badge.className = `admin-badge payment-status-badge ${paymentClasses[status] || "is-unspecified"}`.trim();
    badge.dataset.paymentStatus = status || "UNSPECIFIED";
    badge.textContent = formatPaymentStatus(status);
    return badge;
  }

  function createStatusEditor(reservation) {
    const wrapper = document.createElement("div");
    const select = document.createElement("select");
    const button = document.createElement("button");

    wrapper.className = "reservation-status-editor";
    select.className = "reservation-status-select";

    statusOptions.forEach((status) => {
      const option = document.createElement("option");
      option.value = status;
      option.textContent = statusLabels[status];
      option.selected = reservation.reservationStatus === status;
      select.append(option);
    });

    button.type = "button";
    button.className = "btn btn-secondary reservation-status-save";
    button.textContent = "更新";
    button.addEventListener("click", (event) => {
      event.stopPropagation();
      updateStatus(reservation.reservationId, select.value, wrapper.closest("tr"));
    });

    select.addEventListener("click", (event) => event.stopPropagation());
    wrapper.append(select, button);
    return wrapper;
  }

  function appendModalItem(label, value) {
    const wrapper = document.createElement("div");
    const title = document.createElement("strong");
    const content = document.createElement("span");
    title.textContent = label;
    content.textContent = value || "-";
    wrapper.append(title, content);
    modalBody.append(wrapper);
  }

  function openModal(reservation) {
    if (!modal || !modalBody) {
      return;
    }

    modalBody.replaceChildren();
    appendModalItem("予約番号", reservation.reservationCode || `SOA-${reservation.reservationId}`);
    appendModalItem("予約日", formatDate(reservation.reservationDate));
    appendModalItem("時間", formatTime(reservation.timeSlot));
    appendModalItem("顧客名", reservation.customerName);
    appendModalItem("人数", reservation.guestCount ? `${reservation.guestCount}名` : "-");
    appendModalItem("言語", formatLanguage(reservation.guideLanguage));
    appendModalItem("メール", reservation.customerEmail);
    appendModalItem("電話番号", reservation.customerPhone);
    appendModalItem("支払い状態", formatPaymentStatus(reservation.paymentStatus));
    appendModalItem("予約ステータス", statusLabels[reservation.reservationStatus] || reservation.reservationStatus);
    appendModalItem("作成日時", formatDateTime(reservation.createdAt));
    appendModalItem("更新日時", formatDateTime(reservation.updatedAt));
    appendModalItem("メモ", reservation.notes || "-");

    modal.hidden = false;
    modal.setAttribute("aria-hidden", "false");
    document.body.classList.add("modal-open");
  }

  function closeModal() {
    if (!modal) {
      return;
    }
    modal.hidden = true;
    modal.setAttribute("aria-hidden", "true");
    document.body.classList.remove("modal-open");
  }

  function renderReservations() {
    tableBody.replaceChildren();
    const reservations = getSortedReservations();

    if (!reservations.length) {
      const row = document.createElement("tr");
      const cell = document.createElement("td");
      cell.colSpan = 14;
      cell.className = "admin-empty";
      cell.textContent = "条件に一致する予約はありません。";
      row.append(cell);
      tableBody.append(row);
      return;
    }

    reservations.forEach((reservation) => {
      const row = document.createElement("tr");
      row.dataset.reservationId = String(reservation.reservationId);
      row.className = "reservation-row";
      row.tabIndex = 0;

      const basicValues = [
        reservation.reservationCode || `SOA-${reservation.reservationId}`,
        formatDate(reservation.reservationDate),
        formatTime(reservation.timeSlot),
        reservation.customerName || "-",
        reservation.guestCount ?? "-",
        formatLanguage(reservation.guideLanguage),
        reservation.customerEmail || "-",
        reservation.customerPhone || "-",
      ];

      basicValues.forEach((value) => {
        const cell = document.createElement("td");
        cell.textContent = String(value);
        row.append(cell);
      });

      const paymentCell = document.createElement("td");
      paymentCell.append(createPaymentBadge(reservation.paymentStatus));
      row.append(paymentCell);

      const createdCell = document.createElement("td");
      createdCell.textContent = formatDateTime(reservation.createdAt);
      row.append(createdCell);

      const updatedCell = document.createElement("td");
      updatedCell.textContent = formatDateTime(reservation.updatedAt);
      row.append(updatedCell);

      const statusCell = document.createElement("td");
      statusCell.append(createStatusBadge(reservation.reservationStatus));
      row.append(statusCell);

      const editorCell = document.createElement("td");
      editorCell.append(createStatusEditor(reservation));
      row.append(editorCell);

      const noteCell = document.createElement("td");
      noteCell.textContent = reservation.notes || "-";
      row.append(noteCell);

      row.addEventListener("click", () => openModal(reservation));
      row.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          openModal(reservation);
        }
      });

      tableBody.append(row);
    });
  }

  function syncSortUi() {
    if (sortKeySelect) {
      sortKeySelect.value = state.sortKey;
    }
    if (sortDirectionButton) {
      sortDirectionButton.textContent = state.sortDirection === "asc" ? "昇順" : "降順";
    }
  }

  async function loadReservations() {
    setFeedback("予約一覧を読み込み中です。");

    try {
      const query = buildQuery();
      const response = await fetch(`/api/admin/reservations${query ? `?${query}` : ""}`);
      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.message || "予約一覧の取得に失敗しました。");
      }

      const payload = await response.json();
      state.reservations = payload.reservations || [];
      renderReservations();
      if (countBadge) {
        countBadge.textContent = `${payload.totalCount || 0}件`;
      }
      setFeedback(`${payload.totalCount || 0}件の予約を表示しています。`);
    } catch (error) {
      state.reservations = [];
      renderReservations();
      if (countBadge) {
        countBadge.textContent = "0件";
      }
      setFeedback(error.message || "予約一覧の取得に失敗しました。", true);
    }
  }

  async function updateStatus(reservationId, reservationStatus, row) {
    setFeedback(`${reservationId} のステータスを更新しています。`);

    try {
      const response = await fetch(`/api/admin/reservations/${reservationId}/status`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ reservationStatus }),
      });

      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.message || "ステータス更新に失敗しました。");
      }

      const updated = await response.json();
      const target = state.reservations.find((reservation) => reservation.reservationId === updated.reservationId);
      if (target) {
        target.reservationStatus = updated.reservationStatus;
        target.updatedAt = updated.updatedAt;
      }

      const badgeCell = row?.children?.[11];
      if (badgeCell) {
        badgeCell.replaceChildren(createStatusBadge(updated.reservationStatus));
      }
      const updatedCell = row?.children?.[10];
      if (updatedCell && updated.updatedAt) {
        updatedCell.textContent = formatDateTime(updated.updatedAt);
      }

      renderReservations();
      setFeedback(`${updated.reservationCode || `SOA-${updated.reservationId}`} のステータスを ${statusLabels[updated.reservationStatus] || updated.reservationStatus} に更新しました。`);
    } catch (error) {
      setFeedback(error.message || "ステータス更新に失敗しました。", true);
    }
  }

  function exportCsv() {
    const reservations = getSortedReservations();
    if (!reservations.length) {
      setFeedback("CSV 出力できる予約データがありません。", true);
      return;
    }

    const header = [
      "予約番号",
      "予約日",
      "時間",
      "顧客名",
      "人数",
      "言語",
      "メール",
      "電話番号",
      "支払い状態",
      "作成日時",
      "更新日時",
      "予約ステータス",
      "メモ",
    ];
    const lines = [header.join(",")];

    reservations.forEach((reservation) => {
      const record = [
        reservation.reservationCode || `SOA-${reservation.reservationId}`,
        formatDate(reservation.reservationDate),
        formatTime(reservation.timeSlot),
        reservation.customerName || "-",
        reservation.guestCount ?? "-",
        formatLanguage(reservation.guideLanguage),
        reservation.customerEmail || "-",
        reservation.customerPhone || "-",
        formatPaymentStatus(reservation.paymentStatus),
        formatDateTime(reservation.createdAt),
        formatDateTime(reservation.updatedAt),
        statusLabels[reservation.reservationStatus] || reservation.reservationStatus || "-",
        reservation.notes || "-",
      ].map((value) => `"${String(value).replaceAll("\"", "\"\"")}"`);
      lines.push(record.join(","));
    });

    const blob = new Blob(["\uFEFF" + lines.join("\n")], { type: "text/csv;charset=utf-8;" });
    const link = document.createElement("a");
    const url = URL.createObjectURL(blob);
    link.href = url;
    link.download = "reservations.csv";
    link.click();
    URL.revokeObjectURL(url);
    setFeedback("CSV を出力しました。");
  }

  searchButton?.addEventListener("click", (event) => {
    event.preventDefault();
    loadReservations();
  });

  exportButton?.addEventListener("click", (event) => {
    event.preventDefault();
    exportCsv();
  });

  sortKeySelect?.addEventListener("change", () => {
    state.sortKey = sortKeySelect.value;
    renderReservations();
    syncSortUi();
  });

  sortDirectionButton?.addEventListener("click", () => {
    state.sortDirection = state.sortDirection === "asc" ? "desc" : "asc";
    renderReservations();
    syncSortUi();
  });

  modal?.addEventListener("click", (event) => {
    if (event.target instanceof HTMLElement && event.target.dataset.modalClose === "true") {
      closeModal();
    }
  });

  modalCloseButton?.addEventListener("click", closeModal);

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      closeModal();
    }
  });

  syncSortUi();
  loadReservations();
})();
