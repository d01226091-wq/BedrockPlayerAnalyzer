/** @jsxImportSource @bedrock-core/ui */
import { Button, Image, Panel, Screen, Text, useExit, useState } from '@bedrock-core/ui';
import { Card, Header, MenuRow } from '@bedrock-core/ui/ore-styled';

type Page = 'home' | 'worlds' | 'friends' | 'servers' | 'analyzer';

export default function AnalyzerScreen(): JSX.Element {
  const [page, setPage] = useState<Page>('home');
  const exit = useExit();

  const nav = (next: Page) => setPage(next);

  return (
    <Screen>
      <Card variant={'raised'}>
        <Header title={'Bedrock Analyzer'} onClose={exit} />

        <Panel flexDirection={'row'} gap={4}>
          <Panel width={96}>
            <Button onPress={() => nav('worlds')}>
              <Text>{'МИРЫ'}</Text>
            </Button>
            <Button onPress={() => nav('friends')}>
              <Text>{'ИГРА С ДРУГОМ'}</Text>
            </Button>
            <Button onPress={() => nav('servers')}>
              <Text>{'СЕРВЕРЫ'}</Text>
            </Button>
            <Button onPress={() => nav('analyzer')}>
              <Text>{'АНАЛИЗАТОР'}</Text>
            </Button>
          </Panel>

          <Panel flexDirection={'column'} gap={6}>
            {page === 'home' && (
              <>
                <Text>{'PLAYER ANALYTICS'}</Text>
                <Text>{'Анализ работает только по тому, что видно на экране.'}</Text>
                <MenuRow
                  title={'Открыть анализатор'}
                  subtitle={'HUD и наблюдения игроков'}
                  onPress={() => nav('analyzer')}
                />
              </>
            )}

            {page === 'worlds' && (
              <>
                <Text>{'МИРЫ'}</Text>
                <MenuRow
                  title={'Создать мир'}
                  subtitle={'Открывает настоящий Minecraft Bedrock для создания мира'}
                  onPress={exit}
                />
              </>
            )}

            {page === 'friends' && (
              <>
                <Text>{'ИГРА С ДРУГОМ'}</Text>
                <MenuRow
                  title={'Открыть Minecraft'}
                  subtitle={'Выбор друга выполняется в Bedrock'}
                  onPress={exit}
                />
              </>
            )}

            {page === 'servers' && (
              <>
                <Text>{'СЕРВЕРЫ'}</Text>
                <MenuRow
                  title={'Сервер Bedrock'}
                  subtitle={'Добавление адреса выполняется через Minecraft'}
                  onPress={exit}
                />
              </>
            )}

            {page === 'analyzer' && (
              <>
                <Text>{'АНАЛИЗАТОР'}</Text>
                <Text>{'Подозрительность — это эвристическая оценка, а не доказательство читов.'}</Text>
                <MenuRow title={'HUD'} subtitle={'Цветные индикаторы поверх игры'} onPress={exit} />
              </>
            )}
          </Panel>
        </Panel>
      </Card>
    </Screen>
  );
}
