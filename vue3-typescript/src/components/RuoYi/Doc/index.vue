<template>
  <div>
    <svg-icon icon-class="question" @click="goto" />
  </div>
</template>

<script setup lang="ts">
const url = ref<string>('https://opc.cloud/docs')

function goto(): void {
  window.open(url.value)
}
</script>