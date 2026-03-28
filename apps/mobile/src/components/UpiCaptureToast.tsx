import { useEffect, useMemo, useRef } from 'react';
import { Animated, Easing, StyleSheet, View } from 'react-native';
import { Button, Surface, Text } from 'react-native-paper';

export type UpiCaptureDraft = {
  id: string;
  merchantName: string;
  amount: number;
  currency?: string;
  paymentMethod?: string;
  expenseDate?: string;
  categoryId?: string;
  notes?: string;
  tags?: string[];
};

type UpiCaptureToastProps = {
  expense: UpiCaptureDraft;
  onViewDetails: (expense: UpiCaptureDraft) => void;
  onDismiss: () => void;
};

const formatInr = (value: number) =>
  new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(value);

export default function UpiCaptureToast({ expense, onViewDetails, onDismiss }: UpiCaptureToastProps) {
  const translateY = useRef(new Animated.Value(200)).current;
  const dismissTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const inrAmount = useMemo(() => formatInr(expense.amount), [expense.amount]);

  useEffect(() => {
    Animated.timing(translateY, {
      toValue: 0,
      duration: 300,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();

    dismissTimer.current = setTimeout(() => {
      onDismiss();
    }, 8000);

    return () => {
      if (dismissTimer.current) {
        clearTimeout(dismissTimer.current);
      }
    };
  }, [onDismiss, translateY]);

  return (
    <Animated.View style={[styles.wrapper, { transform: [{ translateY }] }]}>
      <Surface style={styles.container} elevation={3}>
        <View style={styles.textBlock}>
          <Text style={styles.title}>UPI payment captured</Text>
          <Text style={styles.subtitle}>{expense.merchantName || 'Unknown merchant'}</Text>
          <Text style={styles.amount}>{inrAmount}</Text>
        </View>

        <View style={styles.actions}>
          <Button mode="contained" onPress={() => onViewDetails(expense)}>
            View Details
          </Button>
          <Button mode="text" onPress={onDismiss}>
            Dismiss
          </Button>
        </View>
      </Surface>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    position: 'absolute',
    left: 12,
    right: 12,
    bottom: 14,
    zIndex: 20,
  },
  container: {
    borderRadius: 14,
    padding: 12,
    backgroundColor: '#ffffff',
  },
  textBlock: {
    marginBottom: 10,
  },
  title: {
    fontWeight: '700',
    marginBottom: 2,
  },
  subtitle: {
    color: '#4b5563',
    marginBottom: 2,
  },
  amount: {
    fontWeight: '700',
    fontSize: 16,
  },
  actions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
});
