import { useMemo, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import Constants from 'expo-constants';
import {
  Button,
  Divider,
  IconButton,
  Modal,
  Portal,
  Snackbar,
  Surface,
  Switch,
  Text,
  TextInput,
} from 'react-native-paper';
import { useAuthStore } from '../store/authStore';

type PlanType = 'Free' | 'Solo' | 'Business';

type NotificationPrefs = {
  push: boolean;
  email: boolean;
  whatsapp: boolean;
};

const upiRegex = /^[a-z0-9.-]+@[a-z]{2,}$/;

export default function SettingsScreen() {
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);

  const [showAddUpiModal, setShowAddUpiModal] = useState(false);
  const [upiInput, setUpiInput] = useState('');
  const [upiError, setUpiError] = useState<string | null>(null);
  const [linkedUpiIds, setLinkedUpiIds] = useState<string[]>([]);
  const [notifications, setNotifications] = useState<NotificationPrefs>({
    push: true,
    email: true,
    whatsapp: false,
  });
  const [plan] = useState<PlanType>('Free');
  const [snackbarMessage, setSnackbarMessage] = useState<string | null>(null);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  const name = useMemo(() => {
    const emailPrefix = user?.email?.split('@')[0];
    if (!emailPrefix) {
      return 'User';
    }

    return emailPrefix.charAt(0).toUpperCase() + emailPrefix.slice(1);
  }, [user?.email]);

  const appVersion = Constants.expoConfig?.version ?? '1.0.0';

  const toggleNotification = (key: keyof NotificationPrefs) => {
    setNotifications((prev) => ({
      ...prev,
      [key]: !prev[key],
    }));
  };

  const handleAddUpi = () => {
    const value = upiInput.trim();

    if (!upiRegex.test(value)) {
      setUpiError('Enter a valid UPI ID (example: name-1@bank).');
      return;
    }

    if (linkedUpiIds.includes(value)) {
      setUpiError('This UPI ID is already linked.');
      return;
    }

    setLinkedUpiIds((prev) => [...prev, value]);
    setShowAddUpiModal(false);
    setUpiInput('');
    setUpiError(null);
    setSnackbarMessage('UPI ID linked successfully.');
  };

  const handleDeleteUpi = (upiId: string) => {
    setLinkedUpiIds((prev) => prev.filter((item) => item !== upiId));
    setSnackbarMessage('UPI ID removed.');
  };

  const handleLogout = async () => {
    setIsLoggingOut(true);
    try {
      await logout();
    } finally {
      setIsLoggingOut(false);
    }
  };

  return (
    <View style={styles.page}>
      <ScrollView contentContainerStyle={styles.contentContainer}>
        <Surface style={styles.sectionCard} elevation={1}>
          <View style={styles.sectionHeaderRow}>
            <Text variant="titleMedium" style={styles.sectionTitle}>
              Profile
            </Text>
            <Button mode="text" compact onPress={() => setSnackbarMessage('Profile editing coming soon.')}> 
              Edit
            </Button>
          </View>
          <Text style={styles.fieldLabel}>Name</Text>
          <Text style={styles.fieldValue}>{name}</Text>
          <Text style={styles.fieldLabel}>Email</Text>
          <Text style={styles.fieldValue}>{user?.email ?? 'Not available'}</Text>
          <Text style={styles.fieldLabel}>Role</Text>
          <Text style={styles.fieldValue}>{user?.role ?? 'employee'}</Text>
        </Surface>

        <Surface style={styles.sectionCard} elevation={1}>
          <View style={styles.sectionHeaderRow}>
            <Text variant="titleMedium" style={styles.sectionTitle}>
              UPI IDs
            </Text>
            <Button mode="contained-tonal" compact onPress={() => setShowAddUpiModal(true)}>
              Add UPI ID
            </Button>
          </View>

          {linkedUpiIds.length === 0 ? (
            <Text style={styles.helperText}>No UPI IDs linked yet.</Text>
          ) : (
            linkedUpiIds.map((upiId, index) => (
              <View key={upiId}>
                <View style={styles.upiRow}>
                  <Text style={styles.upiValue}>{upiId}</Text>
                  <IconButton icon="delete-outline" onPress={() => handleDeleteUpi(upiId)} />
                </View>
                {index < linkedUpiIds.length - 1 ? <Divider /> : null}
              </View>
            ))
          )}
        </Surface>

        <Surface style={styles.sectionCard} elevation={1}>
          <Text variant="titleMedium" style={styles.sectionTitle}>
            Notifications
          </Text>

          <View style={styles.toggleRow}>
            <Text>Push Notifications</Text>
            <Switch value={notifications.push} onValueChange={() => toggleNotification('push')} />
          </View>

          <View style={styles.toggleRow}>
            <Text>Email Notifications</Text>
            <Switch value={notifications.email} onValueChange={() => toggleNotification('email')} />
          </View>

          <View style={styles.toggleRow}>
            <Text>WhatsApp Notifications</Text>
            <Switch
              value={notifications.whatsapp}
              onValueChange={() => toggleNotification('whatsapp')}
            />
          </View>
        </Surface>

        <Surface style={styles.sectionCard} elevation={1}>
          <Text variant="titleMedium" style={styles.sectionTitle}>
            Plan
          </Text>
          <Text style={styles.fieldValue}>Current plan: {plan}</Text>
          <Button mode="contained" onPress={() => setSnackbarMessage('Upgrade flow coming soon.')}> 
            Upgrade
          </Button>
        </Surface>

        <Surface style={styles.sectionCard} elevation={1}>
          <Text variant="titleMedium" style={styles.sectionTitle}>
            About
          </Text>
          <Text style={styles.fieldValue}>App version: {appVersion}</Text>
          <Button
            mode="contained"
            buttonColor="#b91c1c"
            onPress={handleLogout}
            loading={isLoggingOut}
            disabled={isLoggingOut}
          >
            Logout
          </Button>
        </Surface>
      </ScrollView>

      <Portal>
        <Modal
          visible={showAddUpiModal}
          onDismiss={() => {
            setShowAddUpiModal(false);
            setUpiError(null);
          }}
          contentContainerStyle={styles.modalContainer}
        >
          <Text variant="titleMedium" style={styles.modalTitle}>
            Add UPI ID
          </Text>
          <TextInput
            mode="outlined"
            label="UPI ID"
            value={upiInput}
            onChangeText={(value) => {
              setUpiInput(value);
              if (upiError) {
                setUpiError(null);
              }
            }}
            autoCapitalize="none"
            placeholder="example@bank"
            error={Boolean(upiError)}
          />
          {upiError ? <Text style={styles.errorText}>{upiError}</Text> : null}
          <View style={styles.modalActions}>
            <Button mode="text" onPress={() => setShowAddUpiModal(false)}>
              Cancel
            </Button>
            <Button mode="contained" onPress={handleAddUpi}>
              Add
            </Button>
          </View>
        </Modal>
      </Portal>

      <Snackbar visible={Boolean(snackbarMessage)} onDismiss={() => setSnackbarMessage(null)} duration={2200}>
        {snackbarMessage ?? ''}
      </Snackbar>
    </View>
  );
}

const styles = StyleSheet.create({
  page: {
    flex: 1,
    backgroundColor: '#f6f7fb',
  },
  contentContainer: {
    padding: 16,
    paddingBottom: 24,
    gap: 12,
  },
  sectionCard: {
    borderRadius: 12,
    padding: 12,
    gap: 8,
  },
  sectionHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  sectionTitle: {
    fontWeight: '700',
  },
  fieldLabel: {
    color: '#6b7280',
    fontSize: 12,
  },
  fieldValue: {
    color: '#111827',
    marginBottom: 4,
  },
  helperText: {
    color: '#6b7280',
  },
  upiRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  upiValue: {
    flex: 1,
    fontWeight: '500',
  },
  toggleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  modalContainer: {
    backgroundColor: '#fff',
    marginHorizontal: 16,
    borderRadius: 12,
    padding: 16,
    gap: 10,
  },
  modalTitle: {
    fontWeight: '700',
  },
  modalActions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  errorText: {
    color: '#b91c1c',
    fontSize: 12,
  },
});
