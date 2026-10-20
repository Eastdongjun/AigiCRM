// 通用分页条：小程序长列表统一 10/30 条一页，显式上一页/下一页，避免一屏塞满。
// 服务端分页传 total（可带 hasMore 兜底旧接口）；客户端分页传 total=数组长度。
Component({
  options: { addGlobalClass: true },
  properties: {
    page: { type: Number, value: 1 },
    pageSize: { type: Number, value: 10 },
    total: { type: Number, value: 0 },
    hasMore: { type: Boolean, value: false },
    loading: { type: Boolean, value: false },
    sizes: { type: Array, value: [10, 30] },
    unit: { type: String, value: '条' }
  },
  data: {
    totalPages: 1,
    canPrev: false,
    canNext: false,
    sizeItems: []
  },
  observers: {
    'page, pageSize, total, hasMore, sizes': function (page, pageSize, total, hasMore, sizes) {
      const size = pageSize > 0 ? pageSize : 10
      const pages = total > 0 ? Math.max(1, Math.ceil(total / size)) : Math.max(1, page)
      const safePage = Math.min(Math.max(1, page), pages)
      this.setData({
        totalPages: pages,
        canPrev: safePage > 1,
        canNext: total > 0 ? safePage < pages : Boolean(hasMore),
        sizeItems: (sizes && sizes.length ? sizes : [10, 30]).map((value) => ({
          value,
          active: Number(value) === size
        }))
      })
    }
  },
  methods: {
    onPrev() {
      if (!this.data.canPrev) return
      this.triggerEvent('change', { page: this.data.page - 1, pageSize: this.data.pageSize })
    },
    onNext() {
      if (!this.data.canNext) return
      this.triggerEvent('change', { page: this.data.page + 1, pageSize: this.data.pageSize })
    },
    onSize(event) {
      const pageSize = Number(event.currentTarget.dataset.size)
      if (!pageSize || pageSize === this.data.pageSize) return
      this.triggerEvent('change', { page: 1, pageSize })
    }
  }
})
