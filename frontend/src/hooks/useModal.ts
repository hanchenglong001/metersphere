import { Modal } from '@arco-design/web-vue';
import type { ModalConfig } from '@arco-design/web-vue';

export type ModalType = 'info' | 'success' | 'warning' | 'error';

export type ModalSize = 'small' | 'medium' | 'large' | 'full';

export type ModalMode = 'default' | 'weak';

export interface ModalOptions extends ModalConfig {
  mode?: ModalMode;
  type: ModalType;
  size?: ModalSize;
}

export default function useModal() {
  return {
    openModal: (options: ModalOptions) =>
      // error 使用 warning的感叹号图标
      Modal[options.type === 'error' ? 'warning' : options.type]({
        // 默认设置按钮属性，也可通过options传入覆盖
        okButtonProps: {
          type: options.mode === 'weak' ? 'text' : 'primary',
        },
        cancelButtonProps: {
          type: options.mode === 'weak' ? 'text' : 'secondary',
        },
        simple: false,
        ...options,
        titleAlign: 'start',
        modalClass: `ms-usemodal ms-usemodal-${options.mode || 'default'} ms-usemodal-${
          options.size || 'small'
        } ms-usemodal-${options.type}`,
      }),
  };
}