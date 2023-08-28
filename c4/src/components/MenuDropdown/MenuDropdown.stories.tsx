import type { Meta, StoryObj } from "@storybook/react"
import { MenuItemSelectable } from "@carbon/react"
import { within, userEvent } from "@storybook/testing-library"
import { expect } from "@storybook/jest"

import MenuDropdown from "./MenuDropdown"

const meta: Meta<typeof MenuDropdown> = {
	component: MenuDropdown,
	argTypes: {
		size: {
			options: ["sm", "md", "lg"],
			control: { type: "select" },
		},
		disabled: { control: "boolean" },
	},
}

export default meta

type Story = StoryObj<typeof MenuDropdown>

export const mainStory: Story = {
	args: {
		children: [
			<MenuItemSelectable label="item1" />,
			<MenuItemSelectable label="item2" />,
			<MenuItemSelectable label="item3" selected>
				<MenuItemSelectable label="sub item" selected />
			</MenuItemSelectable>,
		],
		label: "Dropdown with submenu",
		size: "sm",
		disabled: false,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement)

		const triggerBtn = canvas.getByText("Dropdown with submenu")
		expect(triggerBtn).not.toBeNull()
	},
}

export const tooManyOptions: Story = {
	args: {
		children: [
			<MenuItemSelectable label="item1" />,
			<MenuItemSelectable label="item2" />,
			<MenuItemSelectable label="item3" selected>
				<MenuItemSelectable label="sub item" selected />
				{Array.from(Array(30).keys()).map((id) => (
					<MenuItemSelectable key={id} label={"sub item" + id} />
				))}
			</MenuItemSelectable>,
		],
		label: "Dropdown with submenu",
		size: "sm",
		disabled: false,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement)
		const triggerBtn = canvas.getByText("Dropdown with submenu")
		await triggerBtn.click()
		const submenu = within(document.querySelector(".MenuDropdownMenu")!)

		expect(submenu.getByText("item3")).not.toBeNull()
		userEvent.hover(submenu.getByText("item3"))
		expect(submenu.getByText("sub item")).not.toBeNull()
	},
}
