import { useEffect, useMemo, useState } from 'react';
import { Platform, ScrollView, StyleSheet, View } from 'react-native';
import { DateTimePickerAndroid } from '@react-native-community/datetimepicker';
import { Controller, SubmitHandler, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigation, useRoute } from '@react-navigation/native';
import { useQuery } from '@tanstack/react-query';
import {
  Button,
  HelperText,
  Menu,
  SegmentedButtons,
  Snackbar,
  Surface,
  Text,
  TextInput,
} from 'react-native-paper';
import { apiClient } from '../api/client';

type CurrencyCode = 'INR' | 'USD' | 'EUR' | 'SGD' | 'AED';
type PaymentMethod = 'UPI' | 'Card' | 'Cash' | 'NEFT';

type CategoryItem = {
  id: string;
  name: string;
};

type RawCategory = Partial<CategoryItem> & {
  categoryId?: string;
  categoryName?: string;
};

type CategoryResponse =
  | CategoryItem[]
  | {
      items?: RawCategory[];
      categories?: RawCategory[];
    };

type AddExpensePrefill = {
  amount?: string;
  currency?: string;
  merchantName?: string;
  paymentMethod?: string;
  expenseDate?: string;
  categoryId?: string;
  notes?: string;
  tags?: string[] | string;
};

const defaultDate = new Date().toISOString().slice(0, 10);

const expenseSchema = z.object({
  amount: z
    .string()
    .trim()
    .min(1, 'Amount is required.')
    .refine((value) => !Number.isNaN(Number(value)), 'Amount must be a valid number.')
    .refine((value) => Number(value) > 0, 'Amount must be greater than 0.'),
  currency: z.enum(['INR', 'USD', 'EUR', 'SGD', 'AED']),
  merchantName: z.string().trim().min(1, 'Merchant name is required.'),
  paymentMethod: z.enum(['UPI', 'Card', 'Cash', 'NEFT']),
  expenseDate: z.string().trim().min(1, 'Expense date is required.'),
  categoryId: z.string().trim().min(1, 'Category is required.'),
  notes: z.string().optional(),
  tags: z.string().optional(),
});

type ExpenseFormValues = z.infer<typeof expenseSchema>;

const currencyOptions: CurrencyCode[] = ['INR', 'USD', 'EUR', 'SGD', 'AED'];

const formatDate = (date: Date) => date.toISOString().slice(0, 10);

export default function AddExpenseScreen() {
  const navigation = useNavigation();
  const route = useRoute();
  const [showCurrencyMenu, setShowCurrencyMenu] = useState(false);
  const [showCategoryMenu, setShowCategoryMenu] = useState(false);
  const [snackbarVisible, setSnackbarVisible] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const params = route.params as { prefill?: AddExpensePrefill } | undefined;

  const {
    control,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ExpenseFormValues>({
    resolver: zodResolver(expenseSchema),
    defaultValues: {
      amount: '',
      currency: 'INR',
      merchantName: '',
      paymentMethod: 'UPI',
      expenseDate: defaultDate,
      categoryId: '',
      notes: '',
      tags: '',
    },
  });

  const categoriesQuery = useQuery({
    queryKey: ['categories'],
    queryFn: async () => {
      const response = await apiClient.get<CategoryResponse>('/v1/categories');
      const payload = response.data;

      const rawList: RawCategory[] = Array.isArray(payload)
        ? payload
        : (payload.items ?? payload.categories ?? []);

      return rawList
        .map((item) => {
          const id = item.id ?? item.categoryId;
          const name = item.name ?? item.categoryName;

          if (!id || !name) {
            return null;
          }

          return { id, name };
        })
        .filter((item): item is CategoryItem => item !== null);
    },
  });

  const categories = categoriesQuery.data ?? [];
  const selectedCategoryId = watch('categoryId');
  const selectedCategoryName = useMemo(
    () => categories.find((category) => category.id === selectedCategoryId)?.name ?? '',
    [categories, selectedCategoryId],
  );

  const openDatePicker = () => {
    if (Platform.OS !== 'android') {
      return;
    }

    const currentValue = watch('expenseDate');
    const currentDate = currentValue ? new Date(currentValue) : new Date();

    DateTimePickerAndroid.open({
      value: Number.isNaN(currentDate.getTime()) ? new Date() : currentDate,
      mode: 'date',
      is24Hour: true,
      onChange: (_, selectedDate) => {
        if (selectedDate) {
          setValue('expenseDate', formatDate(selectedDate), { shouldValidate: true });
        }
      },
    });
  };

  useEffect(() => {
    const prefill = params?.prefill;

    if (!prefill) {
      return;
    }

    const normalizedCurrency = currencyOptions.includes(prefill.currency as CurrencyCode)
      ? (prefill.currency as CurrencyCode)
      : 'INR';

    const normalizedPaymentMethod: PaymentMethod =
      prefill.paymentMethod === 'Card' ||
      prefill.paymentMethod === 'Cash' ||
      prefill.paymentMethod === 'NEFT'
        ? prefill.paymentMethod
        : 'UPI';

    const normalizedTags = Array.isArray(prefill.tags)
      ? prefill.tags.join(', ')
      : (prefill.tags ?? '');

    reset({
      amount: prefill.amount ?? '',
      currency: normalizedCurrency,
      merchantName: prefill.merchantName ?? '',
      paymentMethod: normalizedPaymentMethod,
      expenseDate: prefill.expenseDate || formatDate(new Date()),
      categoryId: prefill.categoryId ?? '',
      notes: prefill.notes ?? '',
      tags: normalizedTags,
    });
  }, [params?.prefill, reset]);

  const onSubmit: SubmitHandler<ExpenseFormValues> = async (values) => {
    setSubmitError(null);

    const payload = {
      amount: Number(values.amount),
      currency: values.currency,
      merchantName: values.merchantName.trim(),
      paymentMethod: values.paymentMethod,
      expenseDate: values.expenseDate,
      categoryId: values.categoryId,
      notes: values.notes?.trim() ? values.notes.trim() : undefined,
      tags: (values.tags ?? '')
        .split(',')
        .map((tag) => tag.trim())
        .filter((tag) => tag.length > 0),
    };

    try {
      await apiClient.post('/v1/expenses', payload);

      reset({
        amount: '',
        currency: 'INR',
        merchantName: '',
        paymentMethod: 'UPI',
        expenseDate: formatDate(new Date()),
        categoryId: '',
        notes: '',
        tags: '',
      });

      setSnackbarVisible(true);
      navigation.navigate('Home' as never);
    } catch {
      setSubmitError('Failed to add expense. Please try again.');
    }
  };

  return (
    <View style={styles.page}>
      <ScrollView
        contentContainerStyle={styles.contentContainer}
        keyboardShouldPersistTaps="handled"
      >
        <Surface style={styles.card} elevation={2}>
          <Text variant="headlineSmall" style={styles.title}>
            Add Expense
          </Text>

          <Controller
            control={control}
            name="amount"
            render={({ field: { onChange, onBlur, value } }) => (
              <TextInput
                label="Amount"
                mode="outlined"
                keyboardType="numeric"
                value={value}
                onBlur={onBlur}
                onChangeText={onChange}
                error={Boolean(errors.amount)}
              />
            )}
          />
          <HelperText type="error" visible={Boolean(errors.amount)}>
            {errors.amount?.message}
          </HelperText>

          <Controller
            control={control}
            name="currency"
            render={({ field: { value, onChange } }) => (
              <Menu
                visible={showCurrencyMenu}
                onDismiss={() => setShowCurrencyMenu(false)}
                anchor={
                  <TextInput
                    label="Currency"
                    mode="outlined"
                    value={value}
                    editable={false}
                    right={<TextInput.Icon icon="chevron-down" onPress={() => setShowCurrencyMenu(true)} />}
                    onPressIn={() => setShowCurrencyMenu(true)}
                  />
                }
              >
                {currencyOptions.map((currency) => (
                  <Menu.Item
                    key={currency}
                    onPress={() => {
                      onChange(currency);
                      setShowCurrencyMenu(false);
                    }}
                    title={currency}
                  />
                ))}
              </Menu>
            )}
          />

          <Controller
            control={control}
            name="merchantName"
            render={({ field: { onChange, onBlur, value } }) => (
              <TextInput
                label="Merchant Name"
                mode="outlined"
                value={value}
                onBlur={onBlur}
                onChangeText={onChange}
                error={Boolean(errors.merchantName)}
              />
            )}
          />
          <HelperText type="error" visible={Boolean(errors.merchantName)}>
            {errors.merchantName?.message}
          </HelperText>

          <Controller
            control={control}
            name="paymentMethod"
            render={({ field: { value, onChange } }) => (
              <View style={styles.segmentContainer}>
                <Text style={styles.segmentLabel}>Payment Method</Text>
                <SegmentedButtons
                  value={value}
                  onValueChange={(nextValue) => onChange(nextValue as PaymentMethod)}
                  buttons={[
                    { value: 'UPI', label: 'UPI' },
                    { value: 'Card', label: 'Card' },
                    { value: 'Cash', label: 'Cash' },
                    { value: 'NEFT', label: 'NEFT' },
                  ]}
                />
              </View>
            )}
          />

          <Controller
            control={control}
            name="expenseDate"
            render={({ field: { value, onChange } }) => (
              <TextInput
                label="Expense Date"
                mode="outlined"
                value={value}
                onChangeText={onChange}
                editable={Platform.OS !== 'android'}
                placeholder="YYYY-MM-DD"
                right={
                  Platform.OS === 'android' ? (
                    <TextInput.Icon icon="calendar" onPress={openDatePicker} />
                  ) : undefined
                }
                onPressIn={Platform.OS === 'android' ? openDatePicker : undefined}
                error={Boolean(errors.expenseDate)}
              />
            )}
          />
          <HelperText type="error" visible={Boolean(errors.expenseDate)}>
            {errors.expenseDate?.message}
          </HelperText>

          <Controller
            control={control}
            name="categoryId"
            render={({ field: { value, onChange } }) => (
              <Menu
                visible={showCategoryMenu}
                onDismiss={() => setShowCategoryMenu(false)}
                anchor={
                  <TextInput
                    label="Category"
                    mode="outlined"
                    value={selectedCategoryName || value}
                    editable={false}
                    right={<TextInput.Icon icon="chevron-down" onPress={() => setShowCategoryMenu(true)} />}
                    onPressIn={() => setShowCategoryMenu(true)}
                    error={Boolean(errors.categoryId)}
                  />
                }
              >
                {categories.length === 0 ? (
                  <Menu.Item title={categoriesQuery.isLoading ? 'Loading...' : 'No categories'} onPress={() => {}} />
                ) : (
                  categories.map((category) => (
                    <Menu.Item
                      key={category.id}
                      onPress={() => {
                        onChange(category.id);
                        setShowCategoryMenu(false);
                      }}
                      title={category.name}
                    />
                  ))
                )}
              </Menu>
            )}
          />
          <HelperText type="error" visible={Boolean(errors.categoryId)}>
            {errors.categoryId?.message}
          </HelperText>

          <Controller
            control={control}
            name="notes"
            render={({ field: { onChange, onBlur, value } }) => (
              <TextInput
                label="Notes"
                mode="outlined"
                multiline
                numberOfLines={3}
                value={value}
                onBlur={onBlur}
                onChangeText={onChange}
              />
            )}
          />

          <Controller
            control={control}
            name="tags"
            render={({ field: { onChange, onBlur, value } }) => (
              <TextInput
                label="Tags (comma separated)"
                mode="outlined"
                value={value}
                onBlur={onBlur}
                onChangeText={onChange}
                style={styles.tagsInput}
              />
            )}
          />

          {submitError ? (
            <Text style={styles.submitErrorText}>{submitError}</Text>
          ) : null}

          <Button
            mode="contained"
            onPress={handleSubmit(onSubmit)}
            loading={isSubmitting}
            disabled={isSubmitting}
          >
            Add Expense
          </Button>
        </Surface>
      </ScrollView>

      <Snackbar
        visible={snackbarVisible}
        onDismiss={() => setSnackbarVisible(false)}
        duration={1800}
        style={styles.successSnackbar}
      >
        Expense added
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
  },
  card: {
    borderRadius: 12,
    padding: 14,
    gap: 4,
  },
  title: {
    textAlign: 'center',
    marginBottom: 8,
  },
  segmentContainer: {
    marginBottom: 8,
  },
  segmentLabel: {
    fontSize: 12,
    color: '#4b5563',
    marginBottom: 6,
    marginLeft: 2,
  },
  tagsInput: {
    marginBottom: 8,
  },
  submitErrorText: {
    color: '#b91c1c',
    fontSize: 13,
    marginBottom: 8,
  },
  successSnackbar: {
    backgroundColor: '#15803d',
  },
});
