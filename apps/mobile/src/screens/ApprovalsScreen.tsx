import { useMemo, useState } from 'react';
import { FlatList, RefreshControl, StyleSheet, View } from 'react-native';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ActivityIndicator,
  Button,
  Modal,
  Portal,
  Snackbar,
  Surface,
  Text,
  TextInput,
} from 'react-native-paper';
import { apiClient } from '../api/client';
import { useAuthStore } from '../store/authStore';

type ApprovalItem = {
  id: string;
  employeeName: string;
  amount: number;
  merchantName: string;
  submittedAt: string;
  dueAt: string;
};

type RawApprovalItem = {
  id?: string;
  approvalId?: string;
  employeeName?: string;
  employee?: { name?: string; email?: string };
  amount?: number;
  merchantName?: string;
  merchant?: string;
  submittedAt?: string;
  submitted_at?: string;
  dueAt?: string;
  due_at?: string;
};

type PendingApprovalsResponse =
  | RawApprovalItem[]
  | {
      items?: RawApprovalItem[];
      approvals?: RawApprovalItem[];
    };

const formatInr = (value: number) =>
  new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(value);

const formatSubmittedDate = (value: string) => {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return parsed.toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const getEscalationText = (dueAt: string) => {
  const dueDate = new Date(dueAt);

  if (Number.isNaN(dueDate.getTime())) {
    return 'Due time unavailable';
  }

  const diffMs = dueDate.getTime() - Date.now();
  const hours = Math.max(1, Math.round(Math.abs(diffMs) / (1000 * 60 * 60)));

  if (diffMs >= 0) {
    return `Due in ${hours} hour${hours > 1 ? 's' : ''}`;
  }

  return `Overdue by ${hours} hour${hours > 1 ? 's' : ''}`;
};

export default function ApprovalsScreen() {
  const queryClient = useQueryClient();
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState<string | null>(null);
  const [rejectModalVisible, setRejectModalVisible] = useState(false);
  const [rejectComment, setRejectComment] = useState('');
  const [selectedApproval, setSelectedApproval] = useState<ApprovalItem | null>(null);
  const [rejectError, setRejectError] = useState<string | null>(null);

  const userRole = useAuthStore((state) => state.user?.role?.toLowerCase() ?? 'employee');
  const isApprover = userRole === 'manager' || userRole === 'admin';

  const pendingApprovalsQuery = useQuery({
    queryKey: ['pending-approvals'],
    queryFn: async () => {
      const response = await apiClient.get<PendingApprovalsResponse>('/v1/approvals/pending');
      const payload = response.data;
      const rawItems = Array.isArray(payload)
        ? payload
        : (payload.items ?? payload.approvals ?? []);

      return rawItems
        .map((item) => {
          const id = item.id ?? item.approvalId;

          if (!id) {
            return null;
          }

          return {
            id,
            employeeName: item.employeeName ?? item.employee?.name ?? item.employee?.email ?? 'Unknown',
            amount: item.amount ?? 0,
            merchantName: item.merchantName ?? item.merchant ?? 'Unknown merchant',
            submittedAt: item.submittedAt ?? item.submitted_at ?? '',
            dueAt: item.dueAt ?? item.due_at ?? '',
          } as ApprovalItem;
        })
        .filter((item): item is ApprovalItem => item !== null);
    },
    enabled: isApprover,
  });

  const approveMutation = useMutation({
    mutationFn: async (approvalId: string) => {
      await apiClient.post(`/v1/approvals/${approvalId}/approve`);
    },
    onSuccess: async () => {
      setSnackbarMessage('Approval submitted successfully.');
      await queryClient.invalidateQueries({ queryKey: ['pending-approvals'] });
    },
  });

  const rejectMutation = useMutation({
    mutationFn: async ({ approvalId, comment }: { approvalId: string; comment: string }) => {
      await apiClient.post(`/v1/approvals/${approvalId}/reject`, { comment });
    },
    onSuccess: async () => {
      setSnackbarMessage('Expense rejected successfully.');
      setRejectModalVisible(false);
      setRejectComment('');
      setSelectedApproval(null);
      setRejectError(null);
      await queryClient.invalidateQueries({ queryKey: ['pending-approvals'] });
    },
  });

  const pendingItems = pendingApprovalsQuery.data ?? [];

  const onRefresh = async () => {
    if (!isApprover) {
      return;
    }

    setIsRefreshing(true);
    try {
      await pendingApprovalsQuery.refetch();
    } finally {
      setIsRefreshing(false);
    }
  };

  const openRejectModal = (item: ApprovalItem) => {
    setSelectedApproval(item);
    setRejectComment('');
    setRejectError(null);
    setRejectModalVisible(true);
  };

  const confirmReject = async () => {
    if (!selectedApproval) {
      return;
    }

    const comment = rejectComment.trim();

    if (!comment) {
      setRejectError('Rejection comment is required.');
      return;
    }

    setRejectError(null);
    await rejectMutation.mutateAsync({ approvalId: selectedApproval.id, comment });
  };

  const loading = pendingApprovalsQuery.isLoading;
  const hasError = pendingApprovalsQuery.isError;

  const emptyStateMessage = useMemo(() => {
    if (!isApprover) {
      return 'You do not have manager approval permissions. Your submitted expenses will appear in Home and History.';
    }

    return 'No pending approvals right now.';
  }, [isApprover]);

  return (
    <View style={styles.page}>
      {!isApprover ? (
        <View style={styles.centerContent}>
          <Surface style={styles.emptyCard} elevation={1}>
            <Text variant="titleMedium" style={styles.emptyTitle}>
              No Approval Tasks
            </Text>
            <Text style={styles.emptyText}>{emptyStateMessage}</Text>
          </Surface>
        </View>
      ) : (
        <FlatList
          data={pendingItems}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.listContainer}
          refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} />}
          ListHeaderComponent={
            <Text variant="headlineSmall" style={styles.title}>
              Pending Approvals
            </Text>
          }
          ListEmptyComponent={
            loading ? (
              <View style={styles.loaderWrap}>
                <ActivityIndicator />
              </View>
            ) : (
              <Surface style={styles.emptyCard} elevation={0}>
                <Text style={styles.emptyText}>{hasError ? 'Failed to load approvals.' : emptyStateMessage}</Text>
              </Surface>
            )
          }
          renderItem={({ item }) => (
            <Surface style={styles.card} elevation={1}>
              <Text style={styles.employeeName}>{item.employeeName}</Text>
              <Text style={styles.expenseText}>
                {formatInr(item.amount)} at {item.merchantName}
              </Text>
              <Text style={styles.metaText}>Submitted: {formatSubmittedDate(item.submittedAt)}</Text>
              <Text style={styles.metaText}>{getEscalationText(item.dueAt)}</Text>

              <View style={styles.actionRow}>
                <Button
                  mode="contained"
                  buttonColor="#15803d"
                  onPress={() => approveMutation.mutate(item.id)}
                  loading={approveMutation.isPending}
                  disabled={approveMutation.isPending || rejectMutation.isPending}
                >
                  Approve
                </Button>
                <Button
                  mode="contained"
                  buttonColor="#b91c1c"
                  onPress={() => openRejectModal(item)}
                  disabled={approveMutation.isPending || rejectMutation.isPending}
                >
                  Reject
                </Button>
              </View>
            </Surface>
          )}
          ItemSeparatorComponent={() => <View style={styles.separator} />}
        />
      )}

      <Portal>
        <Modal
          visible={rejectModalVisible}
          onDismiss={() => setRejectModalVisible(false)}
          contentContainerStyle={styles.modalSheet}
        >
          <Text variant="titleMedium" style={styles.modalTitle}>
            Reject Expense
          </Text>
          <TextInput
            mode="outlined"
            label="Comment"
            value={rejectComment}
            onChangeText={setRejectComment}
            multiline
            numberOfLines={3}
          />
          {rejectError ? <Text style={styles.rejectError}>{rejectError}</Text> : null}
          <View style={styles.modalActions}>
            <Button mode="text" onPress={() => setRejectModalVisible(false)}>
              Cancel
            </Button>
            <Button
              mode="contained"
              buttonColor="#b91c1c"
              onPress={confirmReject}
              loading={rejectMutation.isPending}
              disabled={rejectMutation.isPending}
            >
              Confirm Reject
            </Button>
          </View>
        </Modal>
      </Portal>

      <Snackbar visible={Boolean(snackbarMessage)} onDismiss={() => setSnackbarMessage(null)} duration={2000}>
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
  centerContent: {
    flex: 1,
    justifyContent: 'center',
    padding: 16,
  },
  title: {
    marginBottom: 12,
    fontWeight: '700',
  },
  listContainer: {
    padding: 16,
    flexGrow: 1,
  },
  loaderWrap: {
    paddingVertical: 24,
    alignItems: 'center',
  },
  card: {
    borderRadius: 12,
    padding: 12,
  },
  employeeName: {
    fontWeight: '700',
    marginBottom: 5,
    fontSize: 15,
  },
  expenseText: {
    marginBottom: 6,
    color: '#1f2937',
  },
  metaText: {
    color: '#4b5563',
    fontSize: 12,
    marginBottom: 2,
  },
  actionRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 10,
  },
  separator: {
    height: 10,
  },
  emptyCard: {
    borderRadius: 12,
    padding: 16,
    backgroundColor: '#fff',
  },
  emptyTitle: {
    marginBottom: 6,
    fontWeight: '700',
  },
  emptyText: {
    color: '#6b7280',
  },
  modalSheet: {
    backgroundColor: '#fff',
    marginTop: 'auto',
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    padding: 16,
    gap: 10,
  },
  modalTitle: {
    fontWeight: '700',
  },
  rejectError: {
    color: '#b91c1c',
    fontSize: 12,
  },
  modalActions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
});
