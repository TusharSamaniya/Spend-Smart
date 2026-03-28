import axios from 'axios';
import { useState } from 'react';
import { Controller, SubmitHandler, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { StyleSheet, View } from 'react-native';
import { Button, Surface, Text, TextInput } from 'react-native-paper';
import { apiClient } from '../api/client';
import { AuthUser, useAuthStore } from '../store/authStore';

const loginSchema = z.object({
  email: z.string().email('Please enter a valid email address.'),
  password: z.string().min(8, 'Password must be at least 8 characters.'),
});

type LoginFormValues = z.infer<typeof loginSchema>;

type LoginResponse = {
  accessToken: string;
  refreshToken: string;
  user: AuthUser;
};

export default function LoginScreen() {
  const login = useAuthStore((state) => state.login);
  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  // Local state is used for top-level auth errors that are not tied to a specific field.
  const [authError, setAuthError] = useState<string | null>(null);

  const onSubmit: SubmitHandler<LoginFormValues> = async (values) => {
    setAuthError(null);

    try {
      const response = await apiClient.post<LoginResponse>('/v1/auth/login', values);
      const { accessToken, refreshToken, user } = response.data;

      await login(accessToken, refreshToken, user);
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        setAuthError('Wrong email or password. Please try again.');
        return;
      }

      setAuthError('Unable to login right now. Please try again in a moment.');
    }
  };

  return (
    <View style={styles.container}>
      {authError ? (
        <Surface style={styles.errorBanner} elevation={0}>
          <Text style={styles.errorBannerText}>{authError}</Text>
        </Surface>
      ) : null}

      <Surface style={styles.card} elevation={2}>
        <Text variant="headlineSmall" style={styles.title}>
          Sign In
        </Text>

        <Controller
          control={control}
          name="email"
          render={({ field: { onChange, onBlur, value } }) => (
            <TextInput
              label="Email"
              mode="outlined"
              autoCapitalize="none"
              keyboardType="email-address"
              autoComplete="email"
              value={value}
              onBlur={onBlur}
              onChangeText={onChange}
              error={Boolean(errors.email)}
              style={styles.input}
            />
          )}
        />
        {errors.email ? <Text style={styles.fieldError}>{errors.email.message}</Text> : null}

        <Controller
          control={control}
          name="password"
          render={({ field: { onChange, onBlur, value } }) => (
            <TextInput
              label="Password"
              mode="outlined"
              secureTextEntry
              autoCapitalize="none"
              autoComplete="password"
              value={value}
              onBlur={onBlur}
              onChangeText={onChange}
              error={Boolean(errors.password)}
              style={styles.input}
            />
          )}
        />
        {errors.password ? <Text style={styles.fieldError}>{errors.password.message}</Text> : null}

        <Button
          mode="contained"
          onPress={handleSubmit(onSubmit)}
          loading={isSubmitting}
          disabled={isSubmitting}
          contentStyle={styles.buttonContent}
          style={styles.button}
        >
          Login
        </Button>
      </Surface>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 16,
    backgroundColor: '#f6f7fb',
  },
  errorBanner: {
    marginBottom: 16,
    borderRadius: 10,
    backgroundColor: '#fee2e2',
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  errorBannerText: {
    color: '#b91c1c',
    fontWeight: '600',
  },
  card: {
    borderRadius: 12,
    padding: 16,
  },
  title: {
    marginBottom: 16,
    textAlign: 'center',
  },
  input: {
    marginBottom: 6,
  },
  fieldError: {
    color: '#b91c1c',
    marginBottom: 10,
    marginLeft: 4,
    fontSize: 12,
  },
  button: {
    marginTop: 4,
  },
  buttonContent: {
    paddingVertical: 4,
  },
});
