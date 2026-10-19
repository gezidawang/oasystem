import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 媒体查询组合式。默认返回「是否移动端」(<992px)，与 10 号横切规范断点一致。
 */
export function useMediaQuery(query = '(max-width: 991px)') {
  const matches = ref(false)
  let mql = null
  const update = () => {
    if (mql) matches.value = mql.matches
  }
  onMounted(() => {
    mql = window.matchMedia(query)
    update()
    if (mql.addEventListener) mql.addEventListener('change', update)
    else if (mql.addListener) mql.addListener(update)
  })
  onUnmounted(() => {
    if (!mql) return
    if (mql.removeEventListener) mql.removeEventListener('change', update)
    else if (mql.removeListener) mql.removeListener(update)
  })
  return matches
}

export function useIsMobile() {
  return useMediaQuery('(max-width: 991px)')
}
