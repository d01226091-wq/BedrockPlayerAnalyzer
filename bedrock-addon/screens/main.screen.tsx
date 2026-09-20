/** @jsxImportSource @bedrock-core/ui */
import { Button, Panel, Screen, Text, useExit, useState, type JSX } from '@bedrock-core/ui';
import { Card, Header, MenuRow } from '@bedrock-core/ui/ore-styled';

type Page = 'home' | 'worlds' | 'friends' | 'servers' | 'analyzer';

export default function AnalyzerScreen(): JSX.Element {
  const [page, setPage] = useState<Page>('home');
  const exit = useExit();

  return (
    <Screen>
      <Card variant={'raised'}>
        <Header title={'BEDROCK ANALYZER'} onClose={exit} />
        <Panel flexDirection={'row'} gap={6}>
          <Panel width={180} flexDirection={'column'} gap={3}>
            <Button onPress={() => setPage('home')}><Text>{'ГЛАВНАЯ'}</Text></Button>
            <Button onPress={() => setPage('worlds')}><Text>{'МИРЫ'}</Text></Button>
            <Button onPress={() => setPage('friends')}><Text>{'ИГРА С ДРУГОМ'}</Text></Button>
            <Button onPress={() => setPage('servers')}><Text>{'СЕРВЕРЫ'}</Text></Button>
            <Button onPress={() => setPage('analyzer')}><Text>{'АНАЛИЗАТОР'}</Text></Button>
          </Panel>

          <Panel flexDirection={'column'} gap={6}>
            {page === 'home' && <>
              <Text>{'PLAYER ANALYTICS'}</Text>
              <Text>{'Интерфейс работает прямо внутри мира Minecraft Bedrock.'}</Text>
              <MenuRow title={'Анализ экрана'} subtitle={'HUD, наблюдения и эвристические оценки'} onPress={() => setPage('analyzer')} />
            </>}

            {page === 'worlds' && <>
              <Text>{'МИРЫ'}</Text>
              <MenuRow title={'Создание мира'} subtitle={'Для создания мира используется стандартный Bedrock'} onPress={exit} />
            </>}

            {page === 'friends' && <>
              <Text>{'ИГРА С ДРУГОМ'}</Text>
              <MenuRow title={'Открыть игру'} subtitle={'Дальнейший выбор выполняется в Bedrock'} onPress={exit} />
            </>}

            {page === 'servers' && <>
              <Text>{'СЕРВЕРЫ'}</Text>
              <MenuRow title={'Сервер Bedrock'} subtitle={'Подключение выполняется средствами Bedrock'} onPress={exit} />
            </>}

            {page === 'analyzer' && <>
              <Text>{'АНАЛИЗАТОР'}</Text>
              <Text>{'Оценка является эвристикой и не доказывает наличие читов.'}</Text>
              <MenuRow title={'HUD'} subtitle={'Цветные индикаторы поверх игрового экрана'} onPress={exit} />
            </>}
          </Panel>
        </Panel>
      </Card>
    </Screen>
  );
}
