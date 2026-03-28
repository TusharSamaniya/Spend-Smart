import { useEffect, useRef, useState } from 'react';
import * as Notifications from 'expo-notifications';
import Constants from 'expo-constants';
import type { NavigationContainerRef } from '@react-navigation/native';
import { apiClient } from '../api/client';
import { useAuthStore } from '../store/authStore';
import type { UpiCaptureDraft } from '../components/UpiCaptureToast';

type NotificationData = Record<string, unknown>;

type UsePushNotificationsParams = {
  navigationRef: NavigationContainerRef<ReactNavigation.RootParamList>;
};

const toStringValue = (value: unknown) => {
  if (value === null || value === undefined) {
    return '';
  }

  return String(value);
};

const toNumberValue = (value: unknown) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
};

const toTagsArray = (value: unknown): string[] => {
  if (Array.isArray(value)) {
    return value.map((item) => String(item)).filter((item) => item.length > 0);
  }

  if (typeof value === 'string') {
    return value
      .split(',')
      .map((item) => item.trim())
      .filter((item) => item.length > 0);
  }

  return [];
};

const toDraftFromNotification = (data: NotificationData): UpiCaptureDraft => ({
  id: toStringValue(data.id || data.expenseId || data.approvalId || Date.now()),
  merchantName: toStringValue(data.merchantName || data.merchant || 'Unknown merchant'),
  amount: toNumberValue(data.amount || data.total),
  currency: toStringValue(data.currency || 'INR') || 'INR',
  paymentMethod: toStringValue(data.paymentMethod || 'UPI') || 'UPI',
  expenseDate: toStringValue(data.expenseDate || data.date),
  categoryId: toStringValue(data.categoryId),
  notes: toStringValue(data.notes),
  tags: toTagsArray(data.tags),
});

export default function usePushNotifications({ navigationRef }: UsePushNotificationsParams) {
  const [foregroundExpense, setForegroundExpense] = useState<UpiCaptureDraft | null>(null);
  const permissionCheckedRef = useRef(false);
  const tokenSentRef = useRef(false);

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const expoPushToken = useAuthStore((state) => state.expoPushToken);
  const setExpoPushToken = useAuthStore((state) => state.setExpoPushToken);

  useEffect(() => {
    let mounted = true;

    const receivedSubscription = Notifications.addNotificationReceivedListener((notification) => {
      const data = (notification.request.content.data ?? {}) as NotificationData;
      const type = toStringValue(data.type || data.notificationType);

      if (type === 'approval.requested') {
        setForegroundExpense(toDraftFromNotification(data));
      }
    });

    const responseSubscription = Notifications.addNotificationResponseReceivedListener((response) => {
      const data = (response.notification.request.content.data ?? {}) as NotificationData;
      const type = toStringValue(data.type || data.notificationType);
      const targetScreen = toStringValue(data.screen || data.targetScreen);

      if (!navigationRef.isReady()) {
        return;
      }

      const navigate = navigationRef.navigate as unknown as (
        routeName: string,
        params?: Record<string, unknown>,
      ) => void;

      if (targetScreen === 'Add' || type === 'expense.captured' || type === 'approval.requested') {
        navigate('MainTabs', {
          screen: 'Add',
          params: { prefill: toDraftFromNotification(data) },
        });
        return;
      }

      if (targetScreen === 'Approvals') {
        navigate('MainTabs', { screen: 'Approvals' });
        return;
      }

      navigate('MainTabs', { screen: 'Home' });
    });

    const setupPermissionsAndToken = async () => {
      if (permissionCheckedRef.current) {
        return;
      }

      permissionCheckedRef.current = true;

      const permission = await Notifications.requestPermissionsAsync();

      if (permission.status !== 'granted') {
        return;
      }

      const projectId = Constants.expoConfig?.extra?.eas?.projectId;
      const tokenResponse = await Notifications.getExpoPushTokenAsync(
        projectId ? { projectId } : undefined,
      );

      if (mounted) {
        setExpoPushToken(tokenResponse.data);
      }
    };

    void setupPermissionsAndToken();

    return () => {
      mounted = false;
      receivedSubscription.remove();
      responseSubscription.remove();
    };
  }, [navigationRef, setExpoPushToken]);

  useEffect(() => {
    if (!isAuthenticated || !expoPushToken || tokenSentRef.current) {
      return;
    }

    let mounted = true;

    const syncToken = async () => {
      try {
        await apiClient.patch('/v1/users/device-token', {
          token: expoPushToken,
        });

        if (mounted) {
          tokenSentRef.current = true;
        }
      } catch {
        // Keep silent and allow retry on next app start.
      }
    };

    void syncToken();

    return () => {
      mounted = false;
    };
  }, [expoPushToken, isAuthenticated]);

  const dismissForegroundExpense = () => {
    setForegroundExpense(null);
  };

  return {
    foregroundExpense,
    dismissForegroundExpense,
  };
}
