<template>
  <section class="composer-shell" aria-labelledby="composer-title">
    <label id="composer-title" for="message-input">向小明描述你的问题</label>
    <div class="composer-control">
      <textarea
        id="message-input"
        rows="2"
        disabled
        aria-describedby="composer-help"
        placeholder="业务问答将在后续阶段开放"
      ></textarea>
      <button type="button" disabled aria-label="发送消息，后续阶段开放">发送</button>
    </div>
    <p id="composer-help">不会在本阶段调用 DeepSeek 或发送你的输入。</p>
  </section>
</template>
