import MOCK from '@/utils/setup-mock';

import './user';
import './message-box';
import './api-test';
import './system/user';
import './system/project';
import './system/resourcePool';
import './system/member';

MOCK.onAny().passThrough();
