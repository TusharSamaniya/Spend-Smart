import { useMemo, useState } from 'react';
import { FlatList, RefreshControl, StyleSheet, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { useNavigation } from '@react-navigation/native';
import { ActivityIndicator, Chip, Surface, Text } from 'react-native-paper';
import { apiClient } from '../api/client';
import UpiCaptureToast, { UpiCaptureDraft } from '../components/UpiCaptureToast';
import { useAuthStore } from '../store/authStore';

type SummaryCategory = {
  category: string;
  amount: number;
};

type SummaryApiResponse = {
  totalSpend?: number;
  totalAmount?: number;
  expenseCount?: number;
  totalExpenses?: number;
  pendingApprovals?: number;
  pendingApprovalCount?: number;
  topCategory?: string;
  categoryBreakdown?: SummaryCategory[];
  categories?: SummaryCategory[];
};

type RecentExpense = {
  id: string;
  merchantName: string;
  category: string;
  date: string;
  amount: number;
};

type RecentExpensesResponse = {
  items?: RecentExpense[];
  expenses?: RecentExpense[];
};

type DraftExpense = {
  id: string;
  merchantName?: string;
  amount?: number;
  currency?: string;
  paymentMethod?: string;
  expenseDate?: string;
  categoryId?: string;
  notes?: string;
  tags?: string[];
  createdAt?: string;
  date?: string;
};

type DraftExpensesResponse = {
  items?: DraftExpense[];
  expenses?: DraftExpense[];
};

type MetricsCard = {
  key: string;
  label: string;
  value: string;
};

const formatInr = (value: number) =>
  new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(value);

const formatDate = (value: string) => {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return parsed.toLocaleDateString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
};

const getMonthRange = () => {
  const now = new Date();
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
  const endOfMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0);

  const toIsoDate = (date: Date) => date.toISOString().slice(0, 10);

  return {
    from: toIsoDate(startOfMonth),
    to: toIsoDate(endOfMonth),
  };
};

export default function HomeScreen() {
  const navigation = useNavigation();
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [dismissedUpiDraftId, setDismissedUpiDraftId] = useState<string | null>(null);
  const user = useAuthStore((state) => state.user);

  const monthRange = useMemo(() => getMonthRange(), []);

  const greetingName = useMemo(() => {
    if (!user?.email) {
      return 'there';
    }

    const emailPrefix = user.email.split('@')[0] ?? 'there';
    if (!emailPrefix) {
      return 'there';
    }

    return emailPrefix.charAt(0).toUpperCase() + emailPrefix.slice(1);
  }, [user?.email]);

  const summaryQuery = useQuery({
    queryKey: ['analytics-summary', monthRange.from, monthRange.to],
    queryFn: async () => {
      const response = await apiClient.get<SummaryApiResponse>('/v1/analytics/summary', {
        params: {
          from: monthRange.from,
          to: monthRange.to,
        },
      });

      return response.data;
    },
  });

  const recentExpensesQuery = useQuery({
    queryKey: ['recent-expenses', monthRange.from, monthRange.to],
    queryFn: async () => {
      const response = await apiClient.get<RecentExpensesResponse | RecentExpense[]>(
        '/v1/expenses/recent',
        {
          params: {
            from: monthRange.from,
            to: monthRange.to,
            limit: 5,
          },
        },
      );

      if (Array.isArray(response.data)) {
        return response.data.slice(0, 5);
      }

      const items = response.data.items ?? response.data.expenses ?? [];
      return items.slice(0, 5);
    },
  });

  const upiDraftsQuery = useQuery({
    queryKey: ['upi-auto-drafts'],
    queryFn: async () => {
      const response = await apiClient.get<DraftExpensesResponse | DraftExpense[]>('/v1/expenses', {
        params: {
          status: 'DRAFT',
          source: 'upi_auto',
          since: '60s',
        },
      });

      const drafts = Array.isArray(response.data)
        ? response.data
        : (response.data.items ?? response.data.expenses ?? []);

      return drafts;
    },
    refetchInterval: 10000,
  });

  const latestUpiDraft = useMemo<UpiCaptureDraft | null>(() => {
    const drafts = upiDraftsQuery.data ?? [];

    if (drafts.length === 0) {
      return null;
    }

    const sortedDrafts = [...drafts].sort((a, b) => {
      const aTime = new Date(a.createdAt ?? a.date ?? a.expenseDate ?? 0).getTime();
      const bTime = new Date(b.createdAt ?? b.date ?? b.expenseDate ?? 0).getTime();

      return bTime - aTime;
    });

    const mostRecent = sortedDrafts[0];

    if (!mostRecent?.id || mostRecent.id === dismissedUpiDraftId) {
      return null;
    }

    return {
      id: mostRecent.id,
      merchantName: mostRecent.merchantName ?? 'Unknown merchant',
      amount: mostRecent.amount ?? 0,
      currency: mostRecent.currency ?? 'INR',
      paymentMethod: mostRecent.paymentMethod ?? 'UPI',
      expenseDate: mostRecent.expenseDate ?? mostRecent.date,
      categoryId: mostRecent.categoryId,
      notes: mostRecent.notes,
      tags: mostRecent.tags ?? [],
    };
  }, [dismissedUpiDraftId, upiDraftsQuery.data]);

  const onRefresh = async () => {
    setIsRefreshing(true);
    try {
      await Promise.all([
        summaryQuery.refetch(),
        recentExpensesQuery.refetch(),
        upiDraftsQuery.refetch(),
      ]);
    } finally {
      setIsRefreshing(false);
    }
  };

  const handleDismissUpiToast = () => {
    if (latestUpiDraft) {
      setDismissedUpiDraftId(latestUpiDraft.id);
    }
  };

  const handleViewUpiDetails = (expense: UpiCaptureDraft) => {
    const prefill = {
      amount: String(expense.amount ?? ''),
      currency: expense.currency ?? 'INR',
      merchantName: expense.merchantName ?? '',
      paymentMethod: expense.paymentMethod ?? 'UPI',
      expenseDate: expense.expenseDate ?? '',
      categoryId: expense.categoryId ?? '',
      notes: expense.notes ?? '',
      tags: expense.tags ?? [],
    };

    (navigation as unknown as { navigate: (route: string, params?: object) => void }).navigate(
      'Add',
      { prefill },
    );
    setDismissedUpiDraftId(expense.id);
  };

  const summary = summaryQuery.data;
  const recentExpenses = recentExpensesQuery.data ?? [];

  const totalSpend = summary?.totalSpend ?? summary?.totalAmount ?? 0;
  const expenseCount = summary?.expenseCount ?? summary?.totalExpenses ?? 0;
  const pendingApprovals = summary?.pendingApprovals ?? summary?.pendingApprovalCount ?? 0;
  const topCategory = summary?.topCategory ?? 'N/A';

  const metricCards: MetricsCard[] = [
    {
      key: 'totalSpend',
      label: 'Total Spend This Month',
      value: formatInr(totalSpend),
    },
    {
      key: 'expenseCount',
      label: 'Number of Expenses',
      value: String(expenseCount),
    },
    {
      key: 'pendingApprovals',
      label: 'Pending Approvals',
      value: String(pendingApprovals),
    },
    {
      key: 'topCategory',
      label: 'Top Category',
      value: topCategory,
    },
  ];

  const categories = (summary?.categoryBreakdown ?? summary?.categories ?? []).filter(
    (category) => category.amount > 0,
  );
  const totalCategoryAmount = categories.reduce((acc, item) => acc + item.amount, 0);

  const chartData = categories.map((item) => ({
    ...item,
    percent: totalCategoryAmount > 0 ? (item.amount / totalCategoryAmount) * 100 : 0,
  }));

  const isLoading = summaryQuery.isLoading || recentExpensesQuery.isLoading;
  const hasError = summaryQuery.isError || recentExpensesQuery.isError;

  return (
    <View style={styles.screenContainer}>
      <FlatList
        data={recentExpenses}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.contentContainer}
        refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} />}
        ListHeaderComponent={
          <View>
            <Text variant="headlineSmall" style={styles.greeting}>
              Hello, {greetingName}
            </Text>

            <Text variant="bodyMedium" style={styles.subText}>
              Here is your spend summary for this month.
            </Text>

            {isLoading ? (
              <View style={styles.loaderContainer}>
                <ActivityIndicator />
              </View>
            ) : null}

            {hasError ? (
              <Surface style={styles.errorBanner} elevation={0}>
                <Text style={styles.errorText}>
                  Failed to load analytics data. Pull down to try again.
                </Text>
              </Surface>
            ) : null}

            <View style={styles.cardsGrid}>
              {metricCards.map((card) => (
                <Surface key={card.key} style={styles.metricCard} elevation={1}>
                  <Text style={styles.cardLabel}>{card.label}</Text>
                  <Text style={styles.cardValue}>{card.value}</Text>
                </Surface>
              ))}
            </View>

            <Surface style={styles.chartCard} elevation={1}>
              <Text style={styles.sectionTitle}>Spending by Category</Text>

              {chartData.length === 0 ? (
                <Text style={styles.emptyText}>No category data available for this month.</Text>
              ) : (
                chartData.map((item) => (
                  <View key={`${item.category}-${item.amount}`} style={styles.chartRow}>
                    <Text style={styles.chartCategory}>{item.category}</Text>
                    <View style={styles.barTrack}>
                      <View style={[styles.barFill, { width: `${Math.max(item.percent, 4)}%` }]} />
                    </View>
                    <Text style={styles.chartAmount}>{formatInr(item.amount)}</Text>
                  </View>
                ))
              )}
            </Surface>

            <Text style={styles.sectionTitle}>Recent Expenses</Text>
          </View>
        }
        ListEmptyComponent={
          <Surface style={styles.emptyListCard} elevation={0}>
            <Text style={styles.emptyText}>No recent expenses found.</Text>
          </Surface>
        }
        renderItem={({ item }) => (
          <Surface style={styles.expenseRow} elevation={1}>
            <View style={styles.expenseMain}>
              <Text style={styles.merchantName}>{item.merchantName}</Text>
              <View style={styles.metaRow}>
                <Chip compact style={styles.categoryChip}>
                  {item.category}
                </Chip>
                <Text style={styles.expenseDate}>{formatDate(item.date)}</Text>
              </View>
            </View>
            <Text style={styles.expenseAmount}>{formatInr(item.amount)}</Text>
          </Surface>
        )}
        ItemSeparatorComponent={() => <View style={styles.separator} />}
      />

      {latestUpiDraft ? (
        <UpiCaptureToast
          expense={latestUpiDraft}
          onViewDetails={handleViewUpiDetails}
          onDismiss={handleDismissUpiToast}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screenContainer: {
    flex: 1,
  },
  contentContainer: {
    padding: 16,
    paddingBottom: 24,
    backgroundColor: '#f6f7fb',
    flexGrow: 1,
  },
  greeting: {
    fontWeight: '700',
    marginBottom: 2,
  },
  subText: {
    color: '#4b5563',
    marginBottom: 14,
  },
  loaderContainer: {
    paddingVertical: 14,
    alignItems: 'center',
  },
  errorBanner: {
    marginBottom: 12,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    backgroundColor: '#fee2e2',
  },
  errorText: {
    color: '#b91c1c',
    fontWeight: '600',
  },
  cardsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    gap: 10,
    marginBottom: 14,
  },
  metricCard: {
    width: '48%',
    borderRadius: 12,
    padding: 12,
    minHeight: 90,
    justifyContent: 'space-between',
  },
  cardLabel: {
    color: '#4b5563',
    fontSize: 12,
  },
  cardValue: {
    fontSize: 18,
    fontWeight: '700',
    marginTop: 8,
  },
  chartCard: {
    borderRadius: 12,
    padding: 12,
    marginBottom: 14,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 10,
  },
  chartRow: {
    marginBottom: 10,
  },
  chartCategory: {
    fontSize: 12,
    color: '#374151',
    marginBottom: 5,
  },
  barTrack: {
    width: '100%',
    backgroundColor: '#e5e7eb',
    borderRadius: 999,
    overflow: 'hidden',
    height: 9,
  },
  barFill: {
    height: '100%',
    backgroundColor: '#2563eb',
    borderRadius: 999,
  },
  chartAmount: {
    marginTop: 4,
    fontSize: 12,
    color: '#4b5563',
  },
  expenseRow: {
    borderRadius: 12,
    padding: 12,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  expenseMain: {
    flex: 1,
    marginRight: 10,
  },
  merchantName: {
    fontWeight: '600',
    marginBottom: 8,
    fontSize: 15,
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  categoryChip: {
    alignSelf: 'flex-start',
  },
  expenseDate: {
    color: '#6b7280',
    fontSize: 12,
  },
  expenseAmount: {
    fontWeight: '700',
    fontSize: 15,
  },
  separator: {
    height: 8,
  },
  emptyListCard: {
    borderRadius: 12,
    padding: 14,
    backgroundColor: '#fff',
  },
  emptyText: {
    color: '#6b7280',
  },
});
